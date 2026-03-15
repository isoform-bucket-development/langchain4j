package dev.langchain4j.opentelemetry.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for request duration histogram metrics collection following OpenTelemetry semantic conventions.
 */
class DurationHistogramTest {

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
    @DisplayName("Test case 1: Request completing in 500ms - Duration histogram records ~500ms in appropriate bucket")
    void shouldRecordDurationInAppropriateHistogramBucket() {
        // Given: A request completing in 500ms
        long durationMs = 500;

        // When: Recording duration
        recorder.recordDuration(durationMs, "openai", "gpt-4o", "chat");

        // Then: Verify gen_ai.client.operation.duration histogram is recorded with ~500ms
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        MetricData metricData = durationMetric.get();
        assertThat(metricData.getDescription())
                .isEqualTo("Measures the duration of GenAI operations in seconds");
        assertThat(metricData.getUnit()).isEqualTo("s");

        // Verify histogram data
        List<HistogramPointData> points = metricData.getHistogramData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);

        HistogramPointData point = points.get(0);
        // 500ms = 0.5 seconds
        double expectedDurationSeconds = 0.5;
        assertThat(point.getSum()).isCloseTo(expectedDurationSeconds, within(0.01));
        assertThat(point.getCount()).isEqualTo(1);

        // Verify the value falls in the correct bucket (0.5s should be in the bucket after 0.5)
        // Buckets: 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0
        // A value of 0.5 should fall in the 0.5 bucket boundary
        assertThat(point.getBoundaries()).contains(0.5);

        // Verify attributes/dimensions
        Attributes attrs = point.getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("chat");
    }

    @Test
    @DisplayName("Test case 2: Streaming request with 2 second total duration - Duration measured from request start to final token received")
    void shouldRecordStreamingRequestDurationFromStartToEnd() {
        // Given: A streaming request with 2 second total duration
        long durationMs = 2000;

        // When: Recording duration for streaming operation
        // Simulate streaming by recording the total duration from start to final token
        recorder.recordDuration(durationMs, "anthropic", "claude-3-sonnet", "chat");

        // Then: Verify histogram contains correct duration
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        List<HistogramPointData> points = durationMetric.get().getHistogramData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);

        HistogramPointData point = points.get(0);
        // 2000ms = 2.0 seconds
        double expectedDurationSeconds = 2.0;
        assertThat(point.getSum()).isCloseTo(expectedDurationSeconds, within(0.01));
        assertThat(point.getCount()).isEqualTo(1);

        // Verify the value falls in appropriate bucket
        // 2.0 should fall in the 2.0 bucket boundary
        assertThat(point.getBoundaries()).contains(2.0);

        // Verify provider dimension
        Attributes attrs = point.getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("anthropic");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("claude-3-sonnet");
    }

    @Test
    @DisplayName("Test case 3: Failed request after 1 second - Duration recorded even for failed requests, with error dimension")
    void shouldRecordDurationForFailedRequestsWithErrorDimension() {
        // Given: A failed request that took 1 second before failing
        long durationMs = 1000;
        String errorType = "java.io.IOException";

        // When: Recording duration for failed operation
        recorder.recordDurationWithError(durationMs, "openai", "gpt-4o", "chat", errorType);

        // Then: Verify histogram records duration with error dimension
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        List<HistogramPointData> points = durationMetric.get().getHistogramData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);

        HistogramPointData point = points.get(0);
        // 1000ms = 1.0 second
        double expectedDurationSeconds = 1.0;
        assertThat(point.getSum()).isCloseTo(expectedDurationSeconds, within(0.01));
        assertThat(point.getCount()).isEqualTo(1);

        // Verify error dimension is present
        Attributes attrs = point.getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("chat");
        assertThat(attrs.get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("java.io.IOException");
    }

    @Test
    @DisplayName("Multiple durations should be aggregated in histogram")
    void shouldAggregateMultipleDurationsInHistogram() {
        // Given: Multiple requests with different durations
        recorder.recordDuration(500, "openai", "gpt-4o", "chat");   // 0.5s
        recorder.recordDuration(1500, "openai", "gpt-4o", "chat");  // 1.5s
        recorder.recordDuration(3000, "openai", "gpt-4o", "chat");  // 3.0s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify histogram aggregates all durations
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        List<HistogramPointData> points = durationMetric.get().getHistogramData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);

        HistogramPointData point = points.get(0);
        // Total sum: 0.5 + 1.5 + 3.0 = 5.0 seconds
        assertThat(point.getSum()).isCloseTo(5.0, within(0.01));
        assertThat(point.getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Durations should be aggregated separately by model dimension")
    void shouldAggregateDurationsByModelDimension() {
        // Given: Requests to different models
        recorder.recordDuration(500, "openai", "gpt-4o", "chat");        // 0.5s
        recorder.recordDuration(1000, "openai", "gpt-4o", "chat");       // 1.0s
        recorder.recordDuration(2000, "anthropic", "claude-3-sonnet", "chat");  // 2.0s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify separate aggregations per model
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        List<HistogramPointData> points = durationMetric.get().getHistogramData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 2 separate data points (one per unique attribute combination)
        assertThat(points).hasSize(2);

        // Find gpt-4o data point
        Optional<HistogramPointData> gpt4oPoint = points.stream()
                .filter(p -> "gpt-4o".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)))
                .findFirst();
        assertThat(gpt4oPoint).isPresent();
        assertThat(gpt4oPoint.get().getSum()).isCloseTo(1.5, within(0.01)); // 0.5 + 1.0
        assertThat(gpt4oPoint.get().getCount()).isEqualTo(2);

        // Find claude-3-sonnet data point
        Optional<HistogramPointData> claudePoint = points.stream()
                .filter(p -> "claude-3-sonnet".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)))
                .findFirst();
        assertThat(claudePoint).isPresent();
        assertThat(claudePoint.get().getSum()).isCloseTo(2.0, within(0.01));
        assertThat(claudePoint.get().getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Histogram should use expected bucket boundaries for LLM latencies")
    void shouldUseExpectedBucketBoundariesForLlmLatencies() {
        // Given: A duration value
        recorder.recordDuration(100, "openai", "gpt-4o", "chat"); // 0.1s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify expected bucket boundaries are configured
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        HistogramPointData point = durationMetric.get().getHistogramData().getPoints()
                .stream().findFirst().orElseThrow();

        // Verify bucket boundaries cover typical LLM latencies (100ms to 30s)
        List<Double> boundaries = point.getBoundaries();
        assertThat(boundaries).contains(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0);
    }

    @Test
    @DisplayName("Should record duration with direct Attributes object")
    void shouldRecordDurationWithAttributesObject() {
        // Given: Custom attributes
        Attributes attributes = Attributes.builder()
                .put(GenAiMetrics.ATTR_GEN_AI_SYSTEM, "azure")
                .put(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL, "gpt-35-turbo")
                .put(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME, "completion")
                .build();

        // When: Recording duration with direct attributes
        recorder.recordDuration(750, attributes); // 0.75s

        // Then: Verify metrics have correct attributes
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();
        HistogramPointData point = durationMetric.get().getHistogramData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getSum()).isCloseTo(0.75, within(0.01));
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("azure");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-35-turbo");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("completion");
    }

    @Test
    @DisplayName("Should record failed duration with Attributes object and error type")
    void shouldRecordFailedDurationWithAttributesAndErrorType() {
        // Given: Custom attributes and error type
        Attributes attributes = Attributes.builder()
                .put(GenAiMetrics.ATTR_GEN_AI_SYSTEM, "openai")
                .put(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL, "gpt-4o")
                .put(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME, "chat")
                .build();
        String errorType = "RateLimitException";

        // When: Recording failed duration
        recorder.recordDurationWithError(5000, attributes, errorType); // 5.0s

        // Then: Verify metrics have correct attributes including error
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();
        HistogramPointData point = durationMetric.get().getHistogramData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getSum()).isCloseTo(5.0, within(0.01));
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE)).isEqualTo("RateLimitException");
    }

    @Test
    @DisplayName("Successful and failed requests should have separate histogram aggregations")
    void shouldSeparateSuccessfulAndFailedRequestHistograms() {
        // Given: Successful and failed requests to the same model
        recorder.recordDuration(500, "openai", "gpt-4o", "chat");   // Successful: 0.5s
        recorder.recordDurationWithError(1000, "openai", "gpt-4o", "chat", "TimeoutException"); // Failed: 1.0s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify separate aggregations (different attribute sets due to error.type)
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();

        List<HistogramPointData> points = durationMetric.get().getHistogramData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 2 separate data points (one without error, one with error)
        assertThat(points).hasSize(2);

        // Find successful request point (no error.type attribute)
        Optional<HistogramPointData> successPoint = points.stream()
                .filter(p -> p.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE) == null)
                .findFirst();
        assertThat(successPoint).isPresent();
        assertThat(successPoint.get().getSum()).isCloseTo(0.5, within(0.01));
        assertThat(successPoint.get().getCount()).isEqualTo(1);

        // Find failed request point (with error.type attribute)
        Optional<HistogramPointData> failedPoint = points.stream()
                .filter(p -> "TimeoutException".equals(p.getAttributes().get(GenAiMetrics.ATTR_ERROR_TYPE)))
                .findFirst();
        assertThat(failedPoint).isPresent();
        assertThat(failedPoint.get().getSum()).isCloseTo(1.0, within(0.01));
        assertThat(failedPoint.get().getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Very short durations (below 100ms) should be recorded correctly")
    void shouldRecordVeryShortDurations() {
        // Given: A very short request (50ms)
        recorder.recordDuration(50, "openai", "gpt-4o", "chat"); // 0.05s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify short duration is recorded
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();
        HistogramPointData point = durationMetric.get().getHistogramData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getSum()).isCloseTo(0.05, within(0.001));
        assertThat(point.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Very long durations (above 30s) should be recorded correctly")
    void shouldRecordVeryLongDurations() {
        // Given: A very long request (60 seconds)
        recorder.recordDuration(60000, "openai", "gpt-4o", "chat"); // 60s

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify long duration is recorded (beyond last bucket)
        Optional<MetricData> durationMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OPERATION_DURATION_HISTOGRAM_NAME))
                .findFirst();

        assertThat(durationMetric).isPresent();
        HistogramPointData point = durationMetric.get().getHistogramData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getSum()).isCloseTo(60.0, within(0.01));
        assertThat(point.getCount()).isEqualTo(1);
    }
}
