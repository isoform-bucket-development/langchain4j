package dev.langchain4j.opentelemetry.error;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for graceful degradation of OpenTelemetry instrumentation.
 * <p>
 * Verifies that LLM functionality continues to work when OpenTelemetry is misconfigured,
 * unavailable, or throws exceptions. The principle is that observability should never
 * break core functionality.
 * </p>
 * <p>
 * NFR-4 from PRD: "If OpenTelemetry SDK is misconfigured or unavailable,
 * the module shall fail gracefully without breaking LLM functionality."
 * </p>
 */
class GracefulDegradationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    private ChatRequest createTestRequest(String message) {
        return ChatRequest.builder()
                .messages(UserMessage.from(message))
                .modelName("gpt-4")
                .build();
    }

    private ChatResponse createTestResponse(String response) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(response))
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(10, 20))
                .build();
    }

    /**
     * Test Case 1: LLM request with broken tracer
     * Expected: Request succeeds, error logged, no span created
     */
    @Test
    @DisplayName("TC1: Should complete LLM request successfully when tracer throws exception during span creation")
    void shouldCompleteRequestWhenTracerThrowsException() {
        // Arrange - Create a mock tracer provider that throws exceptions
        TracerProvider brokenTracerProvider = createBrokenTracerProvider(
                new RuntimeException("Tracer initialization failed: OTel SDK not available"));

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(brokenTracerProvider)
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Hello, how are you?");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = createTestResponse("I'm doing great, thank you!");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Request should complete without throwing
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        });

        // Verify no span was created in the proper extension (the broken provider didn't create any)
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).isEmpty();
    }

    @Test
    @DisplayName("TC1: Should handle null span in response processing gracefully")
    void shouldHandleNullSpanInResponseProcessing() {
        // Arrange - Use a listener that didn't create a span (tracing disabled)
        OpenTelemetryLangChain4jConfig disabledConfig = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .config(disabledConfig)
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Test");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = createTestResponse("Response");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Should not throw even with no span
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        });

        // Verify no span was created (tracing was disabled)
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).isEmpty();
    }

    @Test
    @DisplayName("TC1: Should handle error processing gracefully when no span exists")
    void shouldHandleErrorProcessingWithNoSpan() {
        // Arrange - Use a listener that didn't create a span
        OpenTelemetryLangChain4jConfig disabledConfig = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .config(disabledConfig)
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Test");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        RuntimeException llmError = new RuntimeException("LLM API error");
        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                llmError,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Should not throw even when processing error without span
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onError(errorContext);
        });
    }

    /**
     * Test Case 2: LLM request with null meter provider
     * Expected: Request succeeds with tracing, no metrics
     */
    @Test
    @DisplayName("TC2: Should complete request with tracing when metrics are disabled")
    void shouldCompleteRequestWithTracingWhenMetricsDisabled() {
        // Arrange - Disable metrics in config
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .metricsEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .config(config)
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Metrics disabled test");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.ANTHROPIC,
                attributes
        );

        ChatResponse response = createTestResponse("Response with no metrics");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.ANTHROPIC,
                attributes
        );

        // Act - Should not throw
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        });

        // Assert - Tracing should work (verify span was created)
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("gen_ai.chat");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    /**
     * Test Case 3: Span export failure
     * Expected: LLM functionality unaffected, export error logged
     */
    @Test
    @DisplayName("TC3: Should complete LLM request even when span operations fail")
    void shouldCompleteLlmRequestWhenSpanOperationsFail() {
        // Arrange - Create a tracer provider with a span that throws on operations
        TracerProvider problematicTracerProvider = createTracerProviderWithFailingSpan();

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(problematicTracerProvider)
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Test export failure");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = createTestResponse("Response despite export issues");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Should not throw despite span operation failures
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        });
    }

    @Test
    @DisplayName("TC3: Should handle span.end() without errors")
    void shouldHandleSpanEndGracefully() {
        // Arrange - Use working tracer provider for this test
        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Test span end");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = createTestResponse("Response");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Should not throw
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        });

        // Verify normal completion
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    @DisplayName("TC3: Should maintain LLM functionality when updating config during request")
    void shouldMaintainFunctionalityDuringConfigUpdate() {
        // Arrange
        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .streaming(false)
                .build();

        ChatRequest request = createTestRequest("Test during config update");
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Start the request
        listener.onRequest(requestContext);

        // Update config mid-request (simulate runtime config change)
        listener.updateConfig(OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build());

        // Complete the request
        ChatResponse response = createTestResponse("Response");
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act & Assert - Should complete without issues
        assertThatNoException().isThrownBy(() -> listener.onResponse(responseContext));

        // The span that was started should still be properly ended
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);
    }

    @Test
    @DisplayName("Additional: Should handle ClassNotFoundException scenario gracefully")
    void shouldHandleClassNotFoundGracefully() {
        // This test verifies the design principle - the listener should be designed
        // such that even if OTel classes are not available at runtime, it degrades gracefully

        // For now, test with disabled tracing to simulate unavailable OTel
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                .config(config)
                .build();

        ChatRequest request = createTestRequest("Test");
        Map<Object, Object> attributes = new HashMap<>();

        // Act & Assert - Should handle gracefully
        assertThatNoException().isThrownBy(() -> {
            listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes));
            listener.onResponse(new ChatModelResponseContext(
                    createTestResponse("OK"),
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            ));
        });
    }

    // Helper methods

    /**
     * Creates a tracer provider that throws an exception when trying to create spans.
     */
    private TracerProvider createBrokenTracerProvider(RuntimeException exceptionToThrow) {
        Tracer brokenTracer = mock(Tracer.class);
        SpanBuilder brokenSpanBuilder = mock(SpanBuilder.class);

        // Setup the span builder to throw when startSpan is called
        when(brokenTracer.spanBuilder(anyString())).thenReturn(brokenSpanBuilder);
        when(brokenSpanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(brokenSpanBuilder);
        when(brokenSpanBuilder.setParent(any(Context.class))).thenReturn(brokenSpanBuilder);
        when(brokenSpanBuilder.setAllAttributes(any(Attributes.class))).thenReturn(brokenSpanBuilder);
        when(brokenSpanBuilder.startSpan()).thenThrow(exceptionToThrow);

        TracerProvider brokenProvider = mock(TracerProvider.class);
        when(brokenProvider.get(anyString(), anyString())).thenReturn(brokenTracer);

        return brokenProvider;
    }

    /**
     * Creates a tracer provider where span operations fail.
     */
    private TracerProvider createTracerProviderWithFailingSpan() {
        Span failingSpan = mock(Span.class);

        // Make span operations throw exceptions
        doThrow(new RuntimeException("Span operation failed"))
                .when(failingSpan).setAttribute(any(io.opentelemetry.api.common.AttributeKey.class), any());
        when(failingSpan.isRecording()).thenReturn(true);
        when(failingSpan.getSpanContext()).thenReturn(SpanContext.getInvalid());

        SpanBuilder spanBuilder = mock(SpanBuilder.class);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.setParent(any(Context.class))).thenReturn(spanBuilder);
        when(spanBuilder.setAllAttributes(any(Attributes.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(failingSpan);

        Tracer tracer = mock(Tracer.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);

        TracerProvider provider = mock(TracerProvider.class);
        when(provider.get(anyString(), anyString())).thenReturn(tracer);

        return provider;
    }
}
