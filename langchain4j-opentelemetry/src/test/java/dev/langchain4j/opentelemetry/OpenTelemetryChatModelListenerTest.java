package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * TDD Red Phase: Tests for OpenTelemetry ChatModelListener integration.
 *
 * This test class validates REQ-1 (Automatic Trace Generation) and REQ-2 (Semantic Convention Compliance)
 * for ChatModel operations.
 *
 * Expected behavior: All tests should FAIL until OpenTelemetryChatModelListener is implemented.
 */
class OpenTelemetryChatModelListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener listener;

    @BeforeEach
    void setUp() {
        // This will fail because OpenTelemetryChatModelListener doesn't exist yet
        try {
            listener = new OpenTelemetryChatModelListener(
                otelTesting.getOpenTelemetry().getTracer("test-tracer"),
                otelTesting.getOpenTelemetry().getMeter("test-meter"),
                OpenTelemetryConfig.builder().build()
            );
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            fail("OpenTelemetryChatModelListener class not found - TDD red phase as expected");
        }
    }

    @Test
    void should_create_span_for_successful_chat_request() {
        // Given: REQ-1 - Automatic trace generation for chat model requests
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("What is the capital of France?"))
            .modelName("gpt-4o-mini")
            .temperature(0.7)
            .maxTokens(100)
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);

        // When
        listener.onRequest(requestContext);

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("The capital of France is Paris."))
            .tokenUsage(new TokenUsage(10, 8, 18))
            .finishReason(FinishReason.STOP)
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: REQ-2 - Span follows semantic conventions
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("gen_ai.chat");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify semantic convention attributes
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o-mini");
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_REQUEST_TEMPERATURE)).isEqualTo(0.7);
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_REQUEST_MAX_TOKENS)).isEqualTo(100);
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_RESPONSE_FINISH_REASONS)).contains("stop");
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(10L);
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(8L);

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: OpenTelemetryChatModelListener not implemented yet");
    }

    @Test
    void should_capture_error_in_span_when_chat_request_fails() {
        // Given: REQ-8 - Error and exception tracking
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Test message"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        // When: Error occurs
        RuntimeException error = new RuntimeException("Rate limit exceeded");
        ChatModelErrorContext errorContext = new ChatModelErrorContext(
            error, request, null, requestContext.attributes()
        );
        listener.onError(errorContext);

        // Then: Span records error
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Rate limit exceeded");
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Error handling not implemented yet");
    }

    @Test
    void should_not_capture_message_content_by_default() {
        // Given: REQ-7, NFR-5 - Privacy-first content capture (default: metadata only)
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("This is sensitive user data"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("This is a sensitive AI response"))
            .tokenUsage(new TokenUsage(5, 6, 11))
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Message content should NOT be in span attributes
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        String spanJson = span.toString();
        assertThat(spanJson).doesNotContain("sensitive user data");
        assertThat(spanJson).doesNotContain("sensitive AI response");

        // But metadata should be present
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(5L);

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Content capture configuration not implemented yet");
    }

    @Test
    void should_capture_message_content_when_explicitly_enabled() {
        // Given: REQ-7 - Configurable content capture (opt-in)
        OpenTelemetryConfig config = OpenTelemetryConfig.builder()
            .captureMessageContent(true)
            .build();

        listener = new OpenTelemetryChatModelListener(
            otelTesting.getOpenTelemetry().getTracer("test-tracer"),
            otelTesting.getOpenTelemetry().getMeter("test-meter"),
            config
        );

        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("What is 2+2?"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("2+2 equals 4"))
            .tokenUsage(new TokenUsage(4, 4, 8))
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Message content should be captured
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        // Content should be in events or dedicated attributes
        assertThat(span.getEvents().stream()
            .anyMatch(e -> e.getName().contains("prompt") || e.getName().contains("completion")))
            .isTrue();

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Content capture not implemented yet");
    }

    @Test
    void should_infer_llm_provider_from_model_name() {
        // Given: REQ-2 - gen_ai.system attribute inference
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Test"))
            .modelName("claude-3-5-sonnet")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("Response"))
            .tokenUsage(new TokenUsage(1, 1, 2))
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Should infer provider as "anthropic"
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_SYSTEM)).isEqualTo("anthropic");

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Provider inference not implemented yet");
    }

    @Test
    void should_handle_streaming_chat_model_operations() {
        // Given: REQ-1 - Support for streaming operations
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Write a story"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        // Simulate streaming completion
        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("Once upon a time..."))
            .tokenUsage(new TokenUsage(3, 20, 23))
            .finishReason(FinishReason.STOP)
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Single span with complete token usage
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(SemanticAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(20L);

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Streaming support not implemented yet");
    }

    @Test
    void should_propagate_parent_trace_context() {
        // Given: REQ-4 - Context propagation
        Span parentSpan = otelTesting.getOpenTelemetry()
            .getTracer("test-tracer")
            .spanBuilder("parent-operation")
            .startSpan();

        try (var scope = parentSpan.makeCurrent()) {
            ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Test"))
                .modelName("gpt-4o-mini")
                .build();

            ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
            listener.onRequest(requestContext);

            ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response"))
                .tokenUsage(new TokenUsage(1, 1, 2))
                .build();

            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, requestContext.attributes()
            );
            listener.onResponse(responseContext);
        } finally {
            parentSpan.end();
        }

        // Then: Child span should have parent trace ID
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData parentSpanData = spans.stream()
            .filter(s -> s.getName().equals("parent-operation"))
            .findFirst()
            .orElseThrow();

        SpanData childSpanData = spans.stream()
            .filter(s -> s.getName().equals("gen_ai.chat"))
            .findFirst()
            .orElseThrow();

        assertThat(childSpanData.getTraceId()).isEqualTo(parentSpanData.getTraceId());
        assertThat(childSpanData.getParentSpanId()).isEqualTo(parentSpanData.getSpanId());

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Context propagation not implemented yet");
    }
}
