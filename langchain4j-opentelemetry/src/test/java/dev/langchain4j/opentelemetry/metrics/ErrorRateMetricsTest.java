package dev.langchain4j.opentelemetry.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for error rate metrics collection following OpenTelemetry semantic conventions.
 * <p>
 * Verifies that error counters are incremented with model and provider dimensions,
 * and that error types are properly categorized following semantic conventions.
 * </p>
 */
class ErrorRateMetricsTest {

    private InMemoryMetricReader metricReader;
    private SdkMeterProvider meterProvider;
    private TokenUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        recorder = TokenUsageRecorder.builder()
                .meterProvider(meterProvider)
                .build();
    }

    @Test
    @DisplayName("Test case 1: Rate limit error (HTTP 429) - Error counter incremented with error_type='rate_limit_exceeded'")
    void shouldIncrementErrorCounterForRateLimitError() {
        // Given: A rate limit error (HTTP 429)
        String errorType = "rate_limit_exceeded";
        String system = "openai";
        String model = "gpt-4o";
        String operationName = "chat";

        // When: Recording the error
        recorder.recordError(errorType, system, model, operationName);

        // Then: Verify error counter is incremented with correct error_type
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        MetricData metricData = errorMetric.get();
        assertThat(metricData.getDescription())
                .isEqualTo("Counts the number of errors in GenAI operations");
        assertThat(metricData.getUnit()).isEqualTo("{error}");

        // Verify the counter value and attributes
        List<LongPointData> points = metricData.getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(1);

        // Verify error_type attribute
        Attributes attrs = points.get(0).getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("rate_limit_exceeded");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("Test case 2: API authentication error (HTTP 401) - Error counter with error_type='authentication_error'")
    void shouldIncrementErrorCounterForAuthenticationError() {
        // Given: An authentication error (HTTP 401)
        String errorType = "authentication_error";
        String system = "anthropic";
        String model = "claude-3-sonnet";
        String operationName = "chat";

        // When: Recording the error
        recorder.recordError(errorType, system, model, operationName);

        // Then: Verify error counter is incremented with authentication_error type
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        List<LongPointData> points = errorMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(1);

        // Verify error_type attribute is authentication_error
        Attributes attrs = points.get(0).getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("authentication_error");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("anthropic");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("claude-3-sonnet");
    }

    @Test
    @DisplayName("Test case 3: Request timeout - Error counter with error_type='timeout'")
    void shouldIncrementErrorCounterForTimeout() {
        // Given: A timeout error
        String errorType = "timeout";
        String system = "openai";
        String model = "gpt-4o-mini";
        String operationName = "chat";

        // When: Recording the error
        recorder.recordError(errorType, system, model, operationName);

        // Then: Verify error counter is incremented with timeout type
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        List<LongPointData> points = errorMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(1);

        // Verify error_type attribute is timeout
        Attributes attrs = points.get(0).getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("timeout");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o-mini");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("chat");
    }

    @Test
    @DisplayName("Test case 4: Provider-specific error - Error includes provider dimension for aggregation")
    void shouldIncludeProviderDimensionForErrorAggregation() {
        // Given: Multiple errors from different providers
        // Error 1: OpenAI rate limit
        recorder.recordError("rate_limit_exceeded", "openai", "gpt-4o", "chat");
        // Error 2: OpenAI rate limit (same provider, should aggregate)
        recorder.recordError("rate_limit_exceeded", "openai", "gpt-4o", "chat");
        // Error 3: Anthropic rate limit (different provider, separate aggregation)
        recorder.recordError("rate_limit_exceeded", "anthropic", "claude-3-opus", "chat");
        // Error 4: Azure timeout (different provider and error type)
        recorder.recordError("timeout", "azure", "gpt-4", "chat");

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify errors are aggregated by provider dimension
        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        List<LongPointData> points = errorMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 3 separate data points (different combinations of error_type + provider + model)
        assertThat(points).hasSize(3);

        // Find OpenAI rate limit errors (should be aggregated to 2)
        Optional<LongPointData> openaiRateLimitPoint = points.stream()
                .filter(p -> "openai".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)))
                .filter(p -> "rate_limit_exceeded".equals(p.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE)))
                .findFirst();
        assertThat(openaiRateLimitPoint).isPresent();
        assertThat(openaiRateLimitPoint.get().getValue()).isEqualTo(2);
        assertThat(openaiRateLimitPoint.get().getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4o");

        // Find Anthropic rate limit error
        Optional<LongPointData> anthropicPoint = points.stream()
                .filter(p -> "anthropic".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)))
                .findFirst();
        assertThat(anthropicPoint).isPresent();
        assertThat(anthropicPoint.get().getValue()).isEqualTo(1);
        assertThat(anthropicPoint.get().getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE))
                .isEqualTo("rate_limit_exceeded");
        assertThat(anthropicPoint.get().getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL))
                .isEqualTo("claude-3-opus");

        // Find Azure timeout error
        Optional<LongPointData> azurePoint = points.stream()
                .filter(p -> "azure".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)))
                .findFirst();
        assertThat(azurePoint).isPresent();
        assertThat(azurePoint.get().getValue()).isEqualTo(1);
        assertThat(azurePoint.get().getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE))
                .isEqualTo("timeout");
        assertThat(azurePoint.get().getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("Multiple error types should be tracked separately")
    void shouldTrackMultipleErrorTypesSeparately() {
        // Given: Different error types for the same model/provider
        String system = "openai";
        String model = "gpt-4o";
        String operationName = "chat";

        recorder.recordError("rate_limit_exceeded", system, model, operationName);
        recorder.recordError("authentication_error", system, model, operationName);
        recorder.recordError("timeout", system, model, operationName);
        recorder.recordError("server_error", system, model, operationName);

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Each error type should have its own counter
        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        List<LongPointData> points = errorMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 4 separate data points (one per error type)
        assertThat(points).hasSize(4);

        // Verify each error type is present
        List<String> errorTypes = points.stream()
                .map(p -> p.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE))
                .collect(Collectors.toList());

        assertThat(errorTypes).containsExactlyInAnyOrder(
                "rate_limit_exceeded",
                "authentication_error",
                "timeout",
                "server_error"
        );

        // Each should have count of 1
        points.forEach(point -> assertThat(point.getValue()).isEqualTo(1));
    }

    @Test
    @DisplayName("Error metrics should work with direct Attributes object")
    void shouldRecordErrorWithAttributesObject() {
        // Given: Custom attributes for an error
        Attributes attributes = Attributes.builder()
                .put(GenAiMetrics.ATTR_ERROR_TYPE, "validation_error")
                .put(GenAiMetrics.ATTR_GEN_AI_SYSTEM, "ollama")
                .put(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL, "llama3")
                .put(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME, "completion")
                .build();

        // When: Recording with direct attributes
        recorder.recordError(attributes);

        // Then: Verify metrics have correct attributes
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> errorMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.ERROR_COUNTER_NAME))
                .findFirst();

        assertThat(errorMetric).isPresent();

        LongPointData point = errorMetric.get().getLongSumData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getValue()).isEqualTo(1);
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("validation_error");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("ollama");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("llama3");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("completion");
    }
}
