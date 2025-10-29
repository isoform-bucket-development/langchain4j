package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryMetricsRecorderTest {

    private InMemoryMetricReader metricReader;
    private OpenTelemetry openTelemetry;
    private OpenTelemetryMetricsRecorder metricsRecorder;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
        metricsRecorder = new OpenTelemetryMetricsRecorder(openTelemetry);
    }

    @Test
    void should_record_ai_service_request_metrics() {
        // When
        metricsRecorder.recordAiServiceRequest(
                "TestInterface",
                "testMethod",
                150.5,
                "success"
        );

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotEmpty();

        // Verify counter metric exists
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.aiservice.requests"));

        // Verify histogram metric exists
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.aiservice.duration"));
    }

    @Test
    void should_record_llm_request_metrics() {
        // When
        metricsRecorder.recordLlmRequest("gpt-4", 250.0, "success");

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotEmpty();

        // Verify counter metric exists
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.llm.requests"));

        // Verify histogram metric exists
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.llm.duration"));
    }

    @Test
    void should_record_token_usage_metrics() {
        // When
        metricsRecorder.recordTokenUsage("gpt-4", 100, 50);

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.llm.tokens"));
    }

    @Test
    void should_handle_null_token_values() {
        // When - should not throw exception
        metricsRecorder.recordTokenUsage("gpt-4", null, null);

        // Then - metrics should still be collected (but might be empty)
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotNull();
    }

    @Test
    void should_record_retry_metrics() {
        // When
        metricsRecorder.recordRetry("gpt-4", "TimeoutException");

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).anyMatch(metric ->
                metric.getName().equals("langchain4j.llm.retries"));
    }

    @Test
    void should_record_multiple_requests_with_different_statuses() {
        // When
        metricsRecorder.recordAiServiceRequest("TestInterface", "method1", 100.0, "success");
        metricsRecorder.recordAiServiceRequest("TestInterface", "method1", 200.0, "success");
        metricsRecorder.recordAiServiceRequest("TestInterface", "method2", 150.0, "error");

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void should_record_token_usage_with_zero_values() {
        // When
        metricsRecorder.recordTokenUsage("gpt-4", 0, 0);

        // Then - should not record zero values
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotNull();
    }

    @Test
    void should_handle_null_model_name() {
        // When - should not throw exception
        metricsRecorder.recordLlmRequest(null, 100.0, "success");
        metricsRecorder.recordTokenUsage(null, 10, 20);
        metricsRecorder.recordRetry(null, "Error");

        // Then
        var metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotNull();
    }
}
