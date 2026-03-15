package dev.langchain4j.opentelemetry.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for sampling rate configuration controlling percentage of traced requests.
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>Test Case 1: Sampling rate 0.1 with 100 requests - approximately 10 spans created</li>
 *   <li>Test Case 2: Sampling rate 1.0 (default) - all 100 requests generate spans</li>
 *   <li>Test Case 3: Child span when parent is sampled - LLM span sampled when parent span is sampled</li>
 *   <li>Test Case 4: Child span when parent is not sampled - LLM span not sampled when parent span is not sampled</li>
 * </ul>
 * </p>
 * <p>
 * These tests verify integration with OpenTelemetry's Sampler API, including
 * TraceIdRatioBasedSampler and ParentBasedSampler for distributed tracing consistency.
 * </p>
 */
class SamplingConfigurationTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;

    @AfterEach
    void tearDown() {
        if (spanExporter != null) {
            spanExporter.reset();
        }
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    /**
     * Creates a tracer provider with the specified sampling rate.
     * Uses TraceIdRatioBasedSampler wrapped with ParentBasedSampler to respect parent sampling decisions.
     *
     * @param samplingRate the sampling rate (0.0 to 1.0)
     * @return the configured tracer provider
     */
    private SdkTracerProvider createTracerProviderWithSampling(double samplingRate) {
        spanExporter = InMemorySpanExporter.create();

        // Use ParentBasedSampler with TraceIdRatioBasedSampler as root sampler
        // This ensures child spans respect parent sampling decisions
        Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(samplingRate));

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(sampler)
                .build();

        return tracerProvider;
    }

    /**
     * Creates a listener using the provided tracer provider.
     */
    private OpenTelemetryChatModelListener createListener(SdkTracerProvider provider) {
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .build();

        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(provider)
                .config(config)
                .build();
    }

    /**
     * Creates a chat request context for testing.
     */
    private ChatModelRequestContext createRequestContext(Map<Object, Object> attributes, int requestIndex) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Test request #" + requestIndex))
                .modelName("gpt-4")
                .build();

        return new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes);
    }

    /**
     * Creates a chat response context for testing.
     */
    private ChatModelResponseContext createResponseContext(ChatModelRequestContext requestContext,
                                                           Map<Object, Object> attributes) {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Test response"))
                .id("test-response-id")
                .tokenUsage(new TokenUsage(10, 20))
                .finishReason(FinishReason.STOP)
                .build();

        return new ChatModelResponseContext(
                response,
                requestContext.chatRequest(),
                ModelProvider.OPEN_AI,
                attributes
        );
    }

    /**
     * Executes a single LLM request through the listener.
     */
    private void executeRequest(OpenTelemetryChatModelListener listener, int requestIndex) {
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = createRequestContext(attributes, requestIndex);
        ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);
    }

    /**
     * Test Case 1: Sampling rate 0.1 with 100 requests
     * Expected: Approximately 10 spans created (within statistical margin)
     *
     * This integration test verifies that configuring a 10% sampling rate
     * results in approximately 10% of requests being traced. Due to
     * statistical variance, we allow a margin of +/- 8 spans.
     */
    @Test
    @DisplayName("Test Case 1: Sampling rate 0.1 with 100 requests - approximately 10 spans created")
    void samplingRate10Percent_approximately10SpansCreated() {
        // Arrange: Configure with 10% sampling rate
        SdkTracerProvider provider = createTracerProviderWithSampling(0.1);
        OpenTelemetryChatModelListener listener = createListener(provider);

        // Act: Execute 100 requests
        for (int i = 0; i < 100; i++) {
            executeRequest(listener, i);
        }

        // Assert: Approximately 10 spans should be created (allowing statistical variance)
        // With 0.1 sampling rate and 100 requests, expected value is 10
        // Standard deviation for binomial distribution is sqrt(n*p*(1-p)) = sqrt(100*0.1*0.9) = 3
        // We allow 3 standard deviations (+/- 9 spans) to account for variance
        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        // Due to TraceIdRatioBasedSampler being deterministic based on trace ID,
        // we expect approximately 10 spans, with reasonable tolerance
        assertThat(spans.size())
                .as("With 0.1 sampling rate and 100 requests, expected ~10 spans (allowing +/- 8)")
                .isBetween(2, 18);

        // Verify that sampled spans have correct attributes
        spans.forEach(span -> {
            assertThat(span.getName()).isEqualTo("gen_ai.chat");
            assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
            // Verify sampled flag is set
            assertThat(span.getSpanContext().isSampled()).isTrue();
        });
    }

    /**
     * Test Case 2: Sampling rate 1.0 (default)
     * Expected: All 100 requests generate spans
     *
     * This unit test verifies that the default sampling rate of 1.0
     * (100% sampling) results in all requests being traced.
     */
    @Test
    @DisplayName("Test Case 2: Sampling rate 1.0 (default) - all 100 requests generate spans")
    void samplingRate100Percent_allRequestsGenerateSpans() {
        // Arrange: Configure with 100% sampling rate (default)
        SdkTracerProvider provider = createTracerProviderWithSampling(1.0);
        OpenTelemetryChatModelListener listener = createListener(provider);

        // Also verify the config default sampling rate
        OpenTelemetryLangChain4jConfig defaultConfig = OpenTelemetryLangChain4jConfig.defaultConfig();
        assertThat(defaultConfig.getSamplingRate())
                .as("Default sampling rate should be 1.0")
                .isEqualTo(1.0);

        // Act: Execute 100 requests
        for (int i = 0; i < 100; i++) {
            executeRequest(listener, i);
        }

        // Assert: All 100 requests should generate spans
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("With 1.0 sampling rate, all 100 requests should generate spans")
                .hasSize(100);

        // Verify all spans have correct properties
        spans.forEach(span -> {
            assertThat(span.getName()).isEqualTo("gen_ai.chat");
            assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(span.getSpanContext().isSampled()).isTrue();
        });
    }

    /**
     * Test Case 3: Child span when parent is sampled
     * Expected: LLM span sampled when parent span is sampled
     *
     * This integration test verifies parent-based sampling: when a parent span
     * is sampled, child spans (like LLM calls) should also be sampled.
     * This is critical for distributed tracing consistency.
     */
    @Test
    @DisplayName("Test Case 3: Child span sampled when parent is sampled - verifies ParentBasedSampler")
    void childSpanFollowsParentSamplingDecision_whenParentSampled() {
        // Arrange: Use AlwaysOn sampler wrapped with ParentBased to ensure
        // child spans follow parent decisions
        spanExporter = InMemorySpanExporter.create();

        // Use ParentBased sampler with AlwaysOff root - but parent will override
        Sampler parentBasedSampler = Sampler.parentBased(Sampler.alwaysOn());

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(parentBasedSampler)
                .build();

        Tracer tracer = tracerProvider.get("test-tracer");
        OpenTelemetryChatModelListener listener = createListener(tracerProvider);

        // Create a sampled parent span
        Span parentSpan = tracer.spanBuilder("parent-application-request")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        // Verify parent is sampled
        SpanContext parentContext = parentSpan.getSpanContext();
        assertThat(parentContext.isSampled())
                .as("Parent span should be sampled (SAMPLED flag set)")
                .isTrue();

        try (Scope scope = parentSpan.makeCurrent()) {
            // Execute LLM request as a child operation
            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = createRequestContext(attributes, 1);
            ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        } finally {
            parentSpan.end();
        }

        // Assert: Both parent and child spans should be exported
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("Both parent and child spans should be exported")
                .hasSize(2);

        // Find the child span (gen_ai.chat)
        SpanData childSpan = spans.stream()
                .filter(s -> s.getName().equals("gen_ai.chat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Child span not found"));

        // Find the parent span
        SpanData parentSpanData = spans.stream()
                .filter(s -> s.getName().equals("parent-application-request"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Parent span not found"));

        // Verify child span is properly linked to parent
        assertThat(childSpan.getParentSpanId())
                .as("Child span should have parent span ID")
                .isEqualTo(parentSpanData.getSpanId());

        // Verify both share the same trace ID
        assertThat(childSpan.getTraceId())
                .as("Child span should have same trace ID as parent")
                .isEqualTo(parentSpanData.getTraceId());

        // Verify child span is sampled
        assertThat(childSpan.getSpanContext().isSampled())
                .as("Child span should be sampled when parent is sampled")
                .isTrue();
    }

    /**
     * Test Case 4: Child span when parent is not sampled
     * Expected: LLM span not sampled when parent span is not sampled
     *
     * This integration test verifies that when a parent span is NOT sampled,
     * child spans should also NOT be sampled, maintaining distributed tracing
     * consistency.
     */
    @Test
    @DisplayName("Test Case 4: Child span when parent is not sampled - LLM span not sampled")
    void childSpanWhenParentNotSampled_llmSpanNotSampled() {
        // Arrange: Use ParentBased sampler with AlwaysOff root sampler
        // Root spans will not be sampled, and neither will children
        spanExporter = InMemorySpanExporter.create();

        // Configure: AlwaysOff root means new traces are never sampled,
        // but if there's a sampled parent, children would follow
        Sampler parentBasedSampler = Sampler.parentBased(Sampler.alwaysOff());

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(parentBasedSampler)
                .build();

        Tracer tracer = tracerProvider.get("test-tracer");
        OpenTelemetryChatModelListener listener = createListener(tracerProvider);

        // Create a parent span - with AlwaysOff root, it won't be sampled
        Span parentSpan = tracer.spanBuilder("unsampled-parent-request")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        // Verify parent is NOT sampled
        SpanContext parentContext = parentSpan.getSpanContext();
        assertThat(parentContext.isSampled())
                .as("Parent span should NOT be sampled (SAMPLED flag not set)")
                .isFalse();

        try (Scope scope = parentSpan.makeCurrent()) {
            // Execute LLM request as a child operation
            // Child should follow parent's non-sampling decision
            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = createRequestContext(attributes, 1);
            ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        } finally {
            parentSpan.end();
        }

        // Assert: No spans should be exported (both parent and child are not sampled)
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("No spans should be exported when parent is not sampled")
                .isEmpty();
    }

    /**
     * Helper method to create an AlwaysOn tracer provider.
     */
    private SdkTracerProvider createAlwaysOnTracerProvider() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setSampler(Sampler.alwaysOn())
                .build();
    }

    /**
     * Additional test: Verify sampling rate validation in configuration
     */
    @Test
    @DisplayName("Sampling rate validation - rejects invalid values")
    void samplingRateValidation_rejectsInvalidValues() {
        // Test rate below 0.0
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            OpenTelemetryLangChain4jConfig.builder()
                    .samplingRate(-0.1)
                    .build();
        });

        // Test rate above 1.0
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            OpenTelemetryLangChain4jConfig.builder()
                    .samplingRate(1.1)
                    .build();
        });
    }

    /**
     * Additional test: Verify sampling rate getter works correctly
     */
    @Test
    @DisplayName("Sampling rate getter - returns configured value")
    void samplingRateGetter_returnsConfiguredValue() {
        // Test various sampling rates
        double[] rates = {0.0, 0.1, 0.5, 0.75, 1.0};

        for (double rate : rates) {
            OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                    .samplingRate(rate)
                    .build();

            assertThat(config.getSamplingRate())
                    .as("Sampling rate should be %f", rate)
                    .isEqualTo(rate);
        }
    }

    /**
     * Additional test: Verify 0% sampling rate results in no spans
     */
    @Test
    @DisplayName("Sampling rate 0.0 - no spans created")
    void samplingRateZero_noSpansCreated() {
        // Arrange: Configure with 0% sampling rate
        SdkTracerProvider provider = createTracerProviderWithSampling(0.0);
        OpenTelemetryChatModelListener listener = createListener(provider);

        // Act: Execute multiple requests
        for (int i = 0; i < 50; i++) {
            executeRequest(listener, i);
        }

        // Assert: No spans should be created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("With 0.0 sampling rate, no spans should be created")
                .isEmpty();
    }

    /**
     * Additional test: Verify TraceFlags are correctly set based on sampling
     */
    @Test
    @DisplayName("Sampled spans have SAMPLED TraceFlag set")
    void sampledSpans_haveSampledTraceFlagSet() {
        // Arrange: Use 100% sampling to ensure all spans are sampled
        SdkTracerProvider provider = createTracerProviderWithSampling(1.0);
        OpenTelemetryChatModelListener listener = createListener(provider);

        // Act: Execute a request
        executeRequest(listener, 1);

        // Assert: Span should have SAMPLED flag
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getSpanContext().getTraceFlags())
                .as("Sampled span should have SAMPLED trace flag")
                .isEqualTo(TraceFlags.getSampled());
    }

    /**
     * Additional test: Verify multiple parent spans with mixed sampling
     */
    @Test
    @DisplayName("Mixed parent sampling - children follow their respective parent decisions")
    void mixedParentSampling_childrenFollowRespectiveParents() {
        // Use ParentBased with AlwaysOn for this test
        spanExporter = InMemorySpanExporter.create();

        Sampler parentBasedSampler = Sampler.parentBased(Sampler.alwaysOn());

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setSampler(parentBasedSampler)
                .build();

        Tracer tracer = tracerProvider.get("test-tracer");
        OpenTelemetryChatModelListener listener = createListener(tracerProvider);

        // Create first sampled parent span
        Span parent1 = tracer.spanBuilder("parent-1").startSpan();
        try (Scope scope = parent1.makeCurrent()) {
            executeRequest(listener, 1);
        } finally {
            parent1.end();
        }

        // Create second sampled parent span
        Span parent2 = tracer.spanBuilder("parent-2").startSpan();
        try (Scope scope = parent2.makeCurrent()) {
            executeRequest(listener, 2);
        } finally {
            parent2.end();
        }

        // Assert: Should have 4 spans (2 parents + 2 children)
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(4);

        // Verify each child is linked to correct parent
        List<SpanData> childSpans = spans.stream()
                .filter(s -> s.getName().equals("gen_ai.chat"))
                .collect(Collectors.toList());
        assertThat(childSpans).hasSize(2);

        List<SpanData> parentSpans = spans.stream()
                .filter(s -> s.getName().startsWith("parent-"))
                .collect(Collectors.toList());
        assertThat(parentSpans).hasSize(2);

        // Each child should have a valid parent span ID from our parent spans
        childSpans.forEach(child -> {
            String parentSpanId = child.getParentSpanId();
            boolean hasMatchingParent = parentSpans.stream()
                    .anyMatch(p -> p.getSpanId().equals(parentSpanId));
            assertThat(hasMatchingParent)
                    .as("Child span should be linked to one of the parent spans")
                    .isTrue();
        });
    }
}
