package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for OpenTelemetryMetricsCollector.
 * These tests are designed to fail initially (TDD red phase) and validate:
 * 1. Request counters increment properly
 * 2. Latency histograms record values
 * 3. Token usage metrics are captured
 * 4. Metrics are properly tagged with model provider and status
 */
class OpenTelemetryMetricsCollectorTest {

    private OpenTelemetry openTelemetry;
    private InMemoryMetricReader metricReader;
    private OpenTelemetryMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();

        openTelemetry = OpenTelemetrySdk.builder()
            .setMeterProvider(SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build())
            .build();

        // This will fail because OpenTelemetryMetricsCollector doesn't exist yet
        try {
            Meter meter = openTelemetry.getMeter("langchain4j-metrics-test");
            metricsCollector = new OpenTelemetryMetricsCollector(meter);
        } catch (Exception e) {
            fail("OpenTelemetryMetricsCollector class not yet implemented: " + e.getMessage());
        }
    }

    @Test
    void shouldIncrementAiServiceRequestCounter() {
        // This test will fail because the request counter doesn't exist
        String provider = "openai";
        String model = "gpt-4";

        metricsCollector.recordAiServiceRequest(provider, model, "success");

        var metrics = metricReader.collectAllMetrics();

        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.aiservice.requests")),
            "Should have langchain4j.aiservice.requests counter metric");

        // Verify the counter was incremented with proper attributes
        var requestMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.aiservice.requests"))
            .findFirst()
            .orElseThrow();

        assertEquals(1, requestMetric.getLongSumData().getPoints().size(),
            "Should have one data point for the request");

        var dataPoint = requestMetric.getLongSumData().getPoints().iterator().next();
        assertEquals(1L, dataPoint.getValue(), "Counter should be incremented to 1");

        // Check attributes
        Attributes attributes = dataPoint.getAttributes();
        assertEquals(provider, attributes.get(AttributeKey.stringKey("provider")),
            "Should tag with provider");
        assertEquals(model, attributes.get(AttributeKey.stringKey("model")),
            "Should tag with model");
        assertEquals("success", attributes.get(AttributeKey.stringKey("status")),
            "Should tag with status");
    }

    @Test
    void shouldRecordAiServiceLatency() {
        // This test will fail because the latency histogram doesn't exist
        String provider = "anthropic";
        String model = "claude-3";
        long durationMs = 500;

        metricsCollector.recordAiServiceLatency(provider, model, durationMs);

        var metrics = metricReader.collectAllMetrics();

        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.aiservice.duration")),
            "Should have langchain4j.aiservice.duration histogram metric");

        var latencyMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.aiservice.duration"))
            .findFirst()
            .orElseThrow();

        assertEquals(1, latencyMetric.getHistogramData().getPoints().size(),
            "Should have one histogram data point");

        var dataPoint = latencyMetric.getHistogramData().getPoints().iterator().next();
        assertEquals(1L, dataPoint.getCount(), "Should have recorded one measurement");
        assertEquals(0.5, dataPoint.getSum(), 0.001, "Should record duration in seconds");
    }

    @Test
    void shouldRecordTokenUsageMetrics() {
        // This test will fail because token usage metrics don't exist
        String provider = "openai";
        String model = "gpt-3.5-turbo";
        int inputTokens = 150;
        int outputTokens = 75;

        metricsCollector.recordTokenUsage(provider, model, inputTokens, outputTokens);

        var metrics = metricReader.collectAllMetrics();

        // Check input tokens counter
        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.tokens.input")),
            "Should have langchain4j.tokens.input counter metric");

        // Check output tokens counter
        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.tokens.output")),
            "Should have langchain4j.tokens.output counter metric");

        var inputTokenMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.tokens.input"))
            .findFirst()
            .orElseThrow();

        var inputDataPoint = inputTokenMetric.getLongSumData().getPoints().iterator().next();
        assertEquals(150L, inputDataPoint.getValue(), "Should record input token count");

        var outputTokenMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.tokens.output"))
            .findFirst()
            .orElseThrow();

        var outputDataPoint = outputTokenMetric.getLongSumData().getPoints().iterator().next();
        assertEquals(75L, outputDataPoint.getValue(), "Should record output token count");
    }

    @Test
    void shouldRecordErrorMetrics() {
        // This test will fail because error metrics don't exist
        String provider = "openai";
        String model = "gpt-4";
        String errorType = "RateLimitException";

        metricsCollector.recordError(provider, model, errorType);

        var metrics = metricReader.collectAllMetrics();

        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.errors")),
            "Should have langchain4j.errors counter metric");

        var errorMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.errors"))
            .findFirst()
            .orElseThrow();

        var dataPoint = errorMetric.getLongSumData().getPoints().iterator().next();
        assertEquals(1L, dataPoint.getValue(), "Should increment error counter");

        Attributes attributes = dataPoint.getAttributes();
        assertEquals(errorType, attributes.get(AttributeKey.stringKey("error.type")),
            "Should tag with error type");
    }

    @Test
    void shouldRecordRetryMetrics() {
        // This test will fail because retry metrics don't exist
        String provider = "openai";
        String model = "gpt-4";
        int retryCount = 3;

        metricsCollector.recordRetries(provider, model, retryCount);

        var metrics = metricReader.collectAllMetrics();

        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.retries")),
            "Should have langchain4j.retries counter metric");

        var retryMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.retries"))
            .findFirst()
            .orElseThrow();

        var dataPoint = retryMetric.getLongSumData().getPoints().iterator().next();
        assertEquals(3L, dataPoint.getValue(), "Should record retry count");
    }

    @Test
    void shouldAggregateMetricsAcrossMultipleOperations() {
        // This test will fail because the collector doesn't exist
        String provider = "openai";
        String model = "gpt-4";

        // Record multiple requests
        metricsCollector.recordAiServiceRequest(provider, model, "success");
        metricsCollector.recordAiServiceRequest(provider, model, "success");
        metricsCollector.recordAiServiceRequest(provider, model, "error");

        var metrics = metricReader.collectAllMetrics();

        var requestMetric = metrics.stream()
            .filter(metric -> metric.getName().equals("langchain4j.aiservice.requests"))
            .findFirst()
            .orElseThrow();

        // Should have separate data points for different status values
        var points = requestMetric.getLongSumData().getPoints();
        assertEquals(2, points.size(), "Should have separate points for success and error");

        long successCount = points.stream()
            .filter(point -> "success".equals(point.getAttributes().get(AttributeKey.stringKey("status"))))
            .mapToLong(point -> point.getValue())
            .sum();

        long errorCount = points.stream()
            .filter(point -> "error".equals(point.getAttributes().get(AttributeKey.stringKey("status"))))
            .mapToLong(point -> point.getValue())
            .sum();

        assertEquals(2L, successCount, "Should have 2 successful requests");
        assertEquals(1L, errorCount, "Should have 1 error request");
    }

    @Test
    void shouldHaveMinimalPerformanceOverhead() {
        // This test will fail because metrics collection performance isn't optimized
        String provider = "openai";
        String model = "gpt-4";

        // Measure time to record 1000 metrics
        long startTime = System.nanoTime();

        for (int i = 0; i < 1000; i++) {
            metricsCollector.recordAiServiceRequest(provider, model, "success");
        }

        long durationNs = System.nanoTime() - startTime;
        double durationMs = durationNs / 1_000_000.0;

        assertTrue(durationMs < 100, // Target <0.1ms per metric on average
            "Metrics collection should have minimal overhead, took " + durationMs + "ms for 1000 operations");
    }
}