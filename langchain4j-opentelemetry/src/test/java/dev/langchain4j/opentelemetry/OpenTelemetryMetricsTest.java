package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * TDD Red Phase: Tests for OpenTelemetry Metrics Collection.
 *
 * This test class validates REQ-3 (Metrics Collection) for token usage, duration, and error rates.
 *
 * Expected behavior: All tests should FAIL until OpenTelemetryMetrics is implemented.
 */
class OpenTelemetryMetricsTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener listener;

    @BeforeEach
    void setUp() {
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
    void should_record_token_usage_metrics() {
        // Given: REQ-3 - Token usage metrics
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("Hi there!"))
            .tokenUsage(new TokenUsage(2, 3, 5))
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Metrics should be recorded
        Collection<MetricData> metrics = otelTesting.getMetrics();

        // Find input token counter
        MetricData inputTokens = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
            .filter(m -> hasAttribute(m, "token.type", "input"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Input token metric not found"));

        assertThat(getSumValue(inputTokens)).isEqualTo(2L);

        // Find output token counter
        MetricData outputTokens = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
            .filter(m -> hasAttribute(m, "token.type", "output"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Output token metric not found"));

        assertThat(getSumValue(outputTokens)).isEqualTo(3L);

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Metrics collection not implemented yet");
    }

    @Test
    void should_record_operation_duration_histogram() {
        // Given: REQ-3 - Request duration histograms
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Test"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        long startTime = System.currentTimeMillis();
        listener.onRequest(requestContext);

        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("Response"))
            .tokenUsage(new TokenUsage(1, 1, 2))
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
            response, request, requestContext.attributes()
        );
        listener.onResponse(responseContext);

        // Then: Duration histogram should be recorded
        Collection<MetricData> metrics = otelTesting.getMetrics();

        MetricData durationMetric = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.operation.duration"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Duration metric not found"));

        assertThat(durationMetric.getType()).isEqualTo(MetricData.Type.HISTOGRAM);
        // Duration should be >= 100ms
        assertThat(getHistogramSum(durationMetric)).isGreaterThanOrEqualTo(0.1); // seconds

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Duration metrics not implemented yet");
    }

    @Test
    void should_record_error_rate_metrics() {
        // Given: REQ-3 - Error rates by model and provider
        ChatRequest request = ChatRequest.builder()
            .messages(UserMessage.from("Test"))
            .modelName("gpt-4o-mini")
            .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
        listener.onRequest(requestContext);

        // Simulate error
        RuntimeException error = new RuntimeException("API Error");
        listener.onError(new ChatModelErrorContext(error, request, null, requestContext.attributes()));

        // Then: Error counter should be incremented
        Collection<MetricData> metrics = otelTesting.getMetrics();

        MetricData errorMetric = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.operation.error"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Error metric not found"));

        assertThat(getSumValue(errorMetric)).isEqualTo(1L);

        // Verify error attributes
        assertThat(hasAttribute(errorMetric, "gen_ai.request.model", "gpt-4o-mini")).isTrue();
        assertThat(hasAttribute(errorMetric, "gen_ai.system", "openai")).isTrue();

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Error metrics not implemented yet");
    }

    @Test
    void should_include_model_and_provider_dimensions_in_metrics() {
        // Given: REQ-3 - Metrics include dimensions for model, provider, and application
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

        // Then: Metrics should have model and provider attributes
        Collection<MetricData> metrics = otelTesting.getMetrics();

        MetricData tokenMetric = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
            .findFirst()
            .orElseThrow();

        assertThat(hasAttribute(tokenMetric, "gen_ai.request.model", "claude-3-5-sonnet")).isTrue();
        assertThat(hasAttribute(tokenMetric, "gen_ai.system", "anthropic")).isTrue();
        assertThat(hasAttribute(tokenMetric, "operation_type", "chat")).isTrue();

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Metric dimensions not implemented yet");
    }

    @Test
    void should_aggregate_metrics_across_multiple_requests() {
        // Given: Multiple requests
        for (int i = 0; i < 3; i++) {
            ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Request " + i))
                .modelName("gpt-4o-mini")
                .build();

            ChatModelRequestContext requestContext = new ChatModelRequestContext(request);
            listener.onRequest(requestContext);

            ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response " + i))
                .tokenUsage(new TokenUsage(5, 10, 15))
                .build();

            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, requestContext.attributes()
            );
            listener.onResponse(responseContext);
        }

        // Then: Metrics should be aggregated
        Collection<MetricData> metrics = otelTesting.getMetrics();

        MetricData inputTokens = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
            .filter(m -> hasAttribute(m, "token.type", "input"))
            .findFirst()
            .orElseThrow();

        assertThat(getSumValue(inputTokens)).isEqualTo(15L); // 5 * 3

        MetricData outputTokens = metrics.stream()
            .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
            .filter(m -> hasAttribute(m, "token.type", "output"))
            .findFirst()
            .orElseThrow();

        assertThat(getSumValue(outputTokens)).isEqualTo(30L); // 10 * 3

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Metric aggregation not implemented yet");
    }

    // Helper methods
    private boolean hasAttribute(MetricData metric, String key, String value) {
        // Implementation depends on OTel SDK version
        return false;
    }

    private long getSumValue(MetricData metric) {
        // Implementation depends on metric type
        return 0L;
    }

    private double getHistogramSum(MetricData metric) {
        // Implementation depends on metric type
        return 0.0;
    }
}
