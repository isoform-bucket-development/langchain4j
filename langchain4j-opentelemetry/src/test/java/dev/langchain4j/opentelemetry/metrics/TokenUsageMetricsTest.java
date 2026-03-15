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
 * Tests for token usage metrics collection following OpenTelemetry semantic conventions.
 */
class TokenUsageMetricsTest {

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
    @DisplayName("Test case 1: Token usage counter incremented by total tokens (input + output)")
    void shouldIncrementTokenUsageCounterByTotalTokens() {
        // Given: A request with 50 input tokens and 100 output tokens
        long inputTokens = 50;
        long outputTokens = 100;
        long expectedTotal = 150;

        // When: Recording token usage
        recorder.recordTokenUsage(inputTokens, outputTokens, "openai", "gpt-4o", "chat");

        // Then: Verify gen_ai.client.token.usage counter is incremented by 150 total
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> tokenUsageMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();

        assertThat(tokenUsageMetric).isPresent();

        MetricData metricData = tokenUsageMetric.get();
        assertThat(metricData.getDescription())
                .isEqualTo("Measures the number of tokens used in GenAI operations");
        assertThat(metricData.getUnit()).isEqualTo("{token}");

        // Verify the counter value
        List<LongPointData> points = metricData.getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(expectedTotal);

        // Verify attributes/dimensions
        Attributes attrs = points.get(0).getAttributes();
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(attrs.get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("chat");
    }

    @Test
    @DisplayName("Test case 2: Metrics aggregated by model dimension across multiple requests")
    void shouldAggregateMetricsByModelDimension() {
        // Given: Multiple requests with different models
        // Request 1: gpt-4o model
        recorder.recordTokenUsage(50, 100, "openai", "gpt-4o", "chat");
        // Request 2: gpt-4o model (same model - should aggregate)
        recorder.recordTokenUsage(30, 70, "openai", "gpt-4o", "chat");
        // Request 3: claude-3-sonnet model (different model - separate aggregation)
        recorder.recordTokenUsage(40, 60, "anthropic", "claude-3-sonnet", "chat");

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify metrics are aggregated by model dimension
        Optional<MetricData> tokenUsageMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();

        assertThat(tokenUsageMetric).isPresent();

        List<LongPointData> points = tokenUsageMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 2 separate data points (one per model)
        assertThat(points).hasSize(2);

        // Find gpt-4o data point (should be aggregated: 150 + 100 = 250)
        Optional<LongPointData> gpt4oPoint = points.stream()
                .filter(p -> "gpt-4o".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)))
                .findFirst();
        assertThat(gpt4oPoint).isPresent();
        assertThat(gpt4oPoint.get().getValue()).isEqualTo(250);  // (50+100) + (30+70)
        assertThat(gpt4oPoint.get().getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("openai");

        // Find claude-3-sonnet data point
        Optional<LongPointData> claudePoint = points.stream()
                .filter(p -> "claude-3-sonnet".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)))
                .findFirst();
        assertThat(claudePoint).isPresent();
        assertThat(claudePoint.get().getValue()).isEqualTo(100);  // 40+60
        assertThat(claudePoint.get().getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("Test case 3: Separate counters for input and output tokens")
    void shouldRecordSeparateCountersForInputAndOutputTokens() {
        // Given: A request with input and output tokens
        long inputTokens = 50;
        long outputTokens = 100;

        // When: Recording token usage
        recorder.recordTokenUsage(inputTokens, outputTokens, "openai", "gpt-4o", "chat");

        // Then: Verify separate counters exist for input and output tokens
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Verify input tokens counter (gen_ai.usage.input_tokens)
        Optional<MetricData> inputTokensMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.INPUT_TOKENS_COUNTER_NAME))
                .findFirst();

        assertThat(inputTokensMetric).isPresent();
        assertThat(inputTokensMetric.get().getDescription())
                .isEqualTo("Measures the number of input (prompt) tokens used");

        List<LongPointData> inputPoints = inputTokensMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(inputPoints).hasSize(1);
        assertThat(inputPoints.get(0).getValue()).isEqualTo(inputTokens);

        // Verify output tokens counter (gen_ai.usage.output_tokens)
        Optional<MetricData> outputTokensMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OUTPUT_TOKENS_COUNTER_NAME))
                .findFirst();

        assertThat(outputTokensMetric).isPresent();
        assertThat(outputTokensMetric.get().getDescription())
                .isEqualTo("Measures the number of output (completion) tokens generated");

        List<LongPointData> outputPoints = outputTokensMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(outputPoints).hasSize(1);
        assertThat(outputPoints.get(0).getValue()).isEqualTo(outputTokens);

        // Also verify both counters have proper attributes
        Attributes inputAttrs = inputPoints.get(0).getAttributes();
        assertThat(inputAttrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");

        Attributes outputAttrs = outputPoints.get(0).getAttributes();
        assertThat(outputAttrs.get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("Multiple recordings should accumulate token counts")
    void shouldAccumulateTokenCountsAcrossMultipleRecordings() {
        // Given: Multiple token usage recordings for the same model
        recorder.recordTokenUsage(50, 100, "openai", "gpt-4o", "chat");
        recorder.recordTokenUsage(30, 70, "openai", "gpt-4o", "chat");
        recorder.recordTokenUsage(20, 30, "openai", "gpt-4o", "chat");

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Verify counters are accumulated
        // Input tokens: 50 + 30 + 20 = 100
        Optional<MetricData> inputMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.INPUT_TOKENS_COUNTER_NAME))
                .findFirst();
        assertThat(inputMetric).isPresent();
        LongPointData inputPoint = inputMetric.get().getLongSumData().getPoints()
                .stream().findFirst().orElseThrow();
        assertThat(inputPoint.getValue()).isEqualTo(100);

        // Output tokens: 100 + 70 + 30 = 200
        Optional<MetricData> outputMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OUTPUT_TOKENS_COUNTER_NAME))
                .findFirst();
        assertThat(outputMetric).isPresent();
        LongPointData outputPoint = outputMetric.get().getLongSumData().getPoints()
                .stream().findFirst().orElseThrow();
        assertThat(outputPoint.getValue()).isEqualTo(200);

        // Total tokens: 150 + 100 + 50 = 300
        Optional<MetricData> totalMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();
        assertThat(totalMetric).isPresent();
        LongPointData totalPoint = totalMetric.get().getLongSumData().getPoints()
                .stream().findFirst().orElseThrow();
        assertThat(totalPoint.getValue()).isEqualTo(300);
    }

    @Test
    @DisplayName("Zero token values should not increment counters")
    void shouldNotIncrementCountersForZeroTokens() {
        // Given: A request with zero tokens
        recorder.recordTokenUsage(0, 0, "openai", "gpt-4o", "chat");

        // When: Collecting metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Then: Only total usage counter should be present with value 0
        Optional<MetricData> totalMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();
        assertThat(totalMetric).isPresent();

        // Input and output counters should either not exist or have 0 value
        // (they skip recording when token count is 0)
        Optional<MetricData> inputMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.INPUT_TOKENS_COUNTER_NAME))
                .findFirst();
        Optional<MetricData> outputMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.OUTPUT_TOKENS_COUNTER_NAME))
                .findFirst();

        // Both input and output metrics should not have any data points
        // since we skip recording 0 values
        assertThat(inputMetric).isEmpty();
        assertThat(outputMetric).isEmpty();
    }

    @Test
    @DisplayName("Metrics should work with Attributes object directly")
    void shouldRecordMetricsWithAttributesObject() {
        // Given: Custom attributes
        Attributes attributes = Attributes.builder()
                .put(GenAiMetrics.ATTR_GEN_AI_SYSTEM, "azure")
                .put(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL, "gpt-35-turbo")
                .put(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME, "completion")
                .build();

        // When: Recording with direct attributes
        recorder.recordTokenUsage(100, 200, attributes);

        // Then: Verify metrics have correct attributes
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> totalMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();

        assertThat(totalMetric).isPresent();
        LongPointData point = totalMetric.get().getLongSumData().getPoints()
                .stream().findFirst().orElseThrow();

        assertThat(point.getValue()).isEqualTo(300);
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)).isEqualTo("azure");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-35-turbo");
        assertThat(point.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME)).isEqualTo("completion");
    }
}
