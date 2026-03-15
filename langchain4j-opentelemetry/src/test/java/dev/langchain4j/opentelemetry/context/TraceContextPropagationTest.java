package dev.langchain4j.opentelemetry.context;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.internal.SpanContextManager;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for W3C Trace Context Propagation.
 * <p>
 * Verifies that LLM spans correctly participate in distributed traces with W3C propagation.
 * This includes:
 * <ul>
 *   <li>LLM spans inherit trace context from parent spans</li>
 *   <li>New traces are created when no parent context exists</li>
 *   <li>Nested AiService calls maintain proper parent-child relationships</li>
 *   <li>Context propagation works correctly through streaming callbacks</li>
 * </ul>
 */
class TraceContextPropagationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener listener;
    private Tracer tracer;
    private SpanContextManager spanContextManager;

    @BeforeEach
    void setUp() {
        tracer = otelTesting.getOpenTelemetry().getTracer("test-tracer");
        spanContextManager = new SpanContextManager(tracer);
        listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(false)
                .build();
    }

    /**
     * Test Case 1: LLM call within active trace context.
     * Expected: LLM span has same traceId as parent span.
     */
    @Test
    void llmCallWithinActiveTraceContext_sharesTraceIdWithParent() {
        // Arrange: Create a parent span simulating an HTTP request handler
        Span parentSpan = tracer.spanBuilder("http-request-handler")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello, AI!"))
                .modelName("gpt-4")
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello! How can I help you?"))
                .id("response-001")
                .tokenUsage(new TokenUsage(10, 15))
                .finishReason(FinishReason.STOP)
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act: Invoke LLM within parent span context
        try (Scope scope = parentSpan.makeCurrent()) {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        } finally {
            parentSpan.end();
        }

        // Assert: Verify trace correlation
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData parentSpanData = spans.stream()
                .filter(s -> s.getName().equals("http-request-handler"))
                .findFirst()
                .orElseThrow();

        SpanData llmSpanData = spans.stream()
                .filter(s -> s.getName().equals(GenAiSpanNames.CHAT))
                .findFirst()
                .orElseThrow();

        // LLM span should have the same trace ID as parent
        assertThat(llmSpanData.getTraceId())
                .isEqualTo(parentSpanData.getTraceId());

        // LLM span should have the parent span as its parent
        assertThat(llmSpanData.getParentSpanId())
                .isEqualTo(parentSpanData.getSpanId());

        // Both spans should be in the same trace
        assertThat(llmSpanData.getSpanContext().isValid()).isTrue();
    }

    /**
     * Test Case 2: LLM call with no active context.
     * Expected: New trace created with LLM span as root.
     */
    @Test
    void llmCallWithNoActiveContext_createsNewTraceWithRootSpan() {
        // Arrange: No parent context, just a standalone LLM call
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a joke"))
                .modelName("gpt-4")
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Why did the developer go broke? Because he used up all his cache!"))
                .id("response-002")
                .tokenUsage(new TokenUsage(8, 20))
                .finishReason(FinishReason.STOP)
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act: Invoke LLM without any parent context (Context.root())
        try (Scope scope = Context.root().makeCurrent()) {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        }

        // Assert: Verify new trace was created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData llmSpan = spans.get(0);
        assertThat(llmSpan.getName()).isEqualTo(GenAiSpanNames.CHAT);

        // LLM span should be a root span (no parent)
        assertThat(llmSpan.getParentSpanId())
                .isEqualTo("0000000000000000"); // Invalid parent span ID indicates root

        // Span should have a valid trace ID
        assertThat(llmSpan.getTraceId()).isNotEmpty();
        assertThat(llmSpan.getSpanContext().isValid()).isTrue();
    }

    /**
     * Test Case 3: Nested AiService calls.
     * Expected: Proper parent-child relationship maintained through call stack.
     */
    @Test
    void nestedAiServiceCalls_maintainsProperParentChildRelationship() {
        // Arrange: Simulate nested invocations using SpanContextManager
        UUID outerInvocationId = UUID.randomUUID();
        UUID innerInvocationId = UUID.randomUUID();

        // Act: Create nested spans mimicking AiService -> ChatModel -> nested call
        // Ensure we start from root context to make outer span a true root span
        Span outerSpan;
        try (Scope rootScope = Context.root().makeCurrent()) {
            outerSpan = spanContextManager.startSpan("AiService.outerMethod", outerInvocationId);
        }

        // Within outer span context, start an inner span
        Span innerSpan;
        try (Scope outerScope = outerSpan.makeCurrent()) {
            innerSpan = spanContextManager.startSpan("AiService.innerMethod", innerInvocationId);

            // Simulate some work inside the inner span
            try (Scope innerScope = innerSpan.makeCurrent()) {
                // Create an LLM call within the innermost context
                ChatRequest request = ChatRequest.builder()
                        .messages(UserMessage.from("Nested call"))
                        .modelName("gpt-4")
                        .build();

                ChatResponse response = ChatResponse.builder()
                        .aiMessage(AiMessage.from("Nested response"))
                        .id("response-003")
                        .tokenUsage(new TokenUsage(5, 10))
                        .finishReason(FinishReason.STOP)
                        .build();

                Map<Object, Object> attributes = new HashMap<>();
                ChatModelRequestContext requestContext = new ChatModelRequestContext(
                        request, ModelProvider.OPEN_AI, attributes);
                ChatModelResponseContext responseContext = new ChatModelResponseContext(
                        response, request, ModelProvider.OPEN_AI, attributes);

                listener.onRequest(requestContext);
                listener.onResponse(responseContext);
            }

            spanContextManager.endSpan(innerInvocationId, StatusCode.OK);
        }

        spanContextManager.endSpan(outerInvocationId, StatusCode.OK);

        // Assert: Verify proper hierarchy
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(3);

        SpanData outerSpanData = spans.stream()
                .filter(s -> s.getName().equals("AiService.outerMethod"))
                .findFirst()
                .orElseThrow();

        SpanData innerSpanData = spans.stream()
                .filter(s -> s.getName().equals("AiService.innerMethod"))
                .findFirst()
                .orElseThrow();

        SpanData llmSpanData = spans.stream()
                .filter(s -> s.getName().equals(GenAiSpanNames.CHAT))
                .findFirst()
                .orElseThrow();

        // All spans should share the same trace ID
        assertThat(innerSpanData.getTraceId()).isEqualTo(outerSpanData.getTraceId());
        assertThat(llmSpanData.getTraceId()).isEqualTo(outerSpanData.getTraceId());

        // Inner span should have outer span as parent
        assertThat(innerSpanData.getParentSpanId()).isEqualTo(outerSpanData.getSpanId());

        // LLM span should have inner span as parent
        assertThat(llmSpanData.getParentSpanId()).isEqualTo(innerSpanData.getSpanId());

        // Outer span should be root (no parent)
        assertThat(outerSpanData.getParentSpanId()).isEqualTo("0000000000000000");
    }

    /**
     * Test Case 4: Context propagation through streaming response.
     * Expected: Same trace context available in all streaming callbacks.
     */
    @Test
    void contextPropagationThroughStreamingResponse_maintainsTraceContext() throws InterruptedException {
        // Arrange: Create a streaming listener
        OpenTelemetryChatModelListener streamingListener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(true)
                .build();

        // Create parent span
        Span parentSpan = tracer.spanBuilder("streaming-request-handler")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> callbackTraceId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Stream this"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        // Act: Start request within parent context
        try (Scope scope = parentSpan.makeCurrent()) {
            capturedTraceId.set(parentSpan.getSpanContext().getTraceId());
            streamingListener.onRequest(requestContext);

            // Simulate async streaming callback - context should be preserved
            // The span is stored in attributes and can be retrieved
            Span storedSpan = (Span) attributes.get("otel.span");
            Context storedContext = (Context) attributes.get("otel.context");

            // Simulate streaming callback on different thread
            executor.submit(() -> {
                try {
                    // In a real scenario, context would be propagated via Context.wrap()
                    // Here we verify the stored context is still valid
                    if (storedSpan != null) {
                        callbackTraceId.set(storedSpan.getSpanContext().getTraceId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for callback to complete
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Complete the streaming response
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Streamed content"))
                .id("stream-response-001")
                .tokenUsage(new TokenUsage(5, 50))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        streamingListener.onResponse(responseContext);
        parentSpan.end();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert: Verify trace context was preserved
        assertThat(capturedTraceId.get()).isNotNull();
        assertThat(callbackTraceId.get()).isNotNull();
        assertThat(callbackTraceId.get()).isEqualTo(capturedTraceId.get());

        // Verify spans share the same trace
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData parentSpanData = spans.stream()
                .filter(s -> s.getName().equals("streaming-request-handler"))
                .findFirst()
                .orElseThrow();

        SpanData streamingSpanData = spans.stream()
                .filter(s -> s.getName().equals(GenAiSpanNames.CHAT))
                .findFirst()
                .orElseThrow();

        // Both spans should have the same trace ID
        assertThat(streamingSpanData.getTraceId()).isEqualTo(parentSpanData.getTraceId());

        // Streaming span should have parent span as its parent
        assertThat(streamingSpanData.getParentSpanId()).isEqualTo(parentSpanData.getSpanId());
    }

    /**
     * Additional test: SpanContextManager correctly stores and retrieves context.
     */
    @Test
    void spanContextManager_storesAndRetrievesContext() {
        // Arrange
        UUID invocationId = UUID.randomUUID();

        // Act
        Span span = spanContextManager.startSpan("test-span", invocationId);
        Context storedContext = spanContextManager.getContext(invocationId);
        Span retrievedSpan = spanContextManager.getSpan(invocationId);

        // Assert
        assertThat(retrievedSpan).isNotNull();
        assertThat(retrievedSpan).isEqualTo(span);
        assertThat(storedContext).isNotNull();

        // Cleanup
        spanContextManager.endSpan(invocationId, StatusCode.OK);

        // After ending, span should be removed
        assertThat(spanContextManager.getSpan(invocationId)).isNull();
    }

    /**
     * Additional test: Context is properly propagated via runWithContext.
     */
    @Test
    void runWithContext_executesWithProvidedContext() {
        // Arrange
        UUID invocationId = UUID.randomUUID();
        Span span = spanContextManager.startSpan("parent-context-span", invocationId);
        Context parentContext = spanContextManager.getContext(invocationId);

        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        // Act: Run code with the provided context
        spanContextManager.runWithContext(parentContext, () -> {
            Span currentSpan = Span.current();
            capturedTraceId.set(currentSpan.getSpanContext().getTraceId());
        });

        // Cleanup
        spanContextManager.endSpan(invocationId, StatusCode.OK);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        assertThat(capturedTraceId.get()).isEqualTo(spanData.getTraceId());
    }

    /**
     * Additional test: Verify attributes storage for cross-listener communication.
     */
    @Test
    void storeContextInAttributes_enablesCrossListenerCommunication() {
        // Arrange
        UUID invocationId = UUID.randomUUID();
        Map<Object, Object> attributes = new HashMap<>();

        // Act
        Span span = spanContextManager.startSpan("ai-service-span", invocationId);
        spanContextManager.storeContextInAttributes(attributes, invocationId);

        // Assert
        assertThat(attributes).containsKey("otel.span." + invocationId);
        assertThat(attributes).containsKey("otel.parent.context." + invocationId);

        // Retrieve parent context
        Context retrievedContext = spanContextManager.getParentContextFromAttributes(attributes, invocationId);
        assertThat(retrievedContext).isNotNull();

        // Cleanup
        spanContextManager.endSpan(invocationId, StatusCode.OK);
    }
}
