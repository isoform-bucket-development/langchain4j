package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/**
 * Records OpenTelemetry metrics for LangChain4j operations.
 * Provides metrics for:
 * - Request counts (by operation, model, status)
 * - Latency histograms (for percentile calculation)
 * - Token usage (input/output tokens by model)
 * - Error rates and retry attempts
 * <p>
 * All metrics follow OpenTelemetry naming conventions and include appropriate dimensions
 * for aggregation and filtering.
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryMetricsRecorder {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";

    private final LongCounter aiServiceRequestCounter;
    private final DoubleHistogram aiServiceDurationHistogram;
    private final LongCounter llmRequestCounter;
    private final DoubleHistogram llmDurationHistogram;
    private final LongCounter llmTokenCounter;
    private final LongCounter llmRetryCounter;

    public OpenTelemetryMetricsRecorder(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);

        // AI Service metrics
        this.aiServiceRequestCounter = meter
                .counterBuilder("langchain4j.aiservice.requests")
                .setDescription("Total number of AI Service invocations")
                .setUnit("1")
                .build();

        this.aiServiceDurationHistogram = meter
                .histogramBuilder("langchain4j.aiservice.duration")
                .setDescription("Duration of AI Service invocations")
                .setUnit("ms")
                .build();

        // LLM request metrics
        this.llmRequestCounter = meter
                .counterBuilder("langchain4j.llm.requests")
                .setDescription("Total number of LLM requests")
                .setUnit("1")
                .build();

        this.llmDurationHistogram = meter
                .histogramBuilder("langchain4j.llm.duration")
                .setDescription("Duration of LLM requests")
                .setUnit("ms")
                .build();

        // Token usage metrics
        this.llmTokenCounter = meter
                .counterBuilder("langchain4j.llm.tokens")
                .setDescription("Token usage for LLM requests")
                .setUnit("1")
                .build();

        // Retry metrics
        this.llmRetryCounter = meter
                .counterBuilder("langchain4j.llm.retries")
                .setDescription("Number of LLM request retries")
                .setUnit("1")
                .build();
    }

    /**
     * Records an AI Service request with duration and status.
     *
     * @param interfaceName AI Service interface name
     * @param methodName AI Service method name
     * @param durationMs Duration in milliseconds
     * @param status "success" or "error"
     */
    public void recordAiServiceRequest(String interfaceName, String methodName,
                                       double durationMs, String status) {
        Attributes attributes = Attributes.builder()
                .put("ai_service.interface", interfaceName != null ? interfaceName : "unknown")
                .put("ai_service.method", methodName != null ? methodName : "unknown")
                .put("status", status != null ? status : "unknown")
                .build();

        aiServiceRequestCounter.add(1, attributes);
        aiServiceDurationHistogram.record(durationMs, attributes);
    }

    /**
     * Records an LLM request with duration and status.
     *
     * @param model Model name
     * @param durationMs Duration in milliseconds
     * @param status "success" or "error"
     */
    public void recordLlmRequest(String model, double durationMs, String status) {
        Attributes attributes = Attributes.builder()
                .put("gen_ai.request.model", model != null ? model : "unknown")
                .put("status", status != null ? status : "unknown")
                .build();

        llmRequestCounter.add(1, attributes);
        llmDurationHistogram.record(durationMs, attributes);
    }

    /**
     * Records token usage for an LLM request.
     *
     * @param model Model name
     * @param inputTokens Input token count
     * @param outputTokens Output token count
     */
    public void recordTokenUsage(String model, Integer inputTokens, Integer outputTokens) {
        if (inputTokens != null && inputTokens > 0) {
            Attributes inputAttributes = Attributes.builder()
                    .put("gen_ai.request.model", model != null ? model : "unknown")
                    .put("token_type", "input")
                    .build();
            llmTokenCounter.add(inputTokens, inputAttributes);
        }

        if (outputTokens != null && outputTokens > 0) {
            Attributes outputAttributes = Attributes.builder()
                    .put("gen_ai.request.model", model != null ? model : "unknown")
                    .put("token_type", "output")
                    .build();
            llmTokenCounter.add(outputTokens, outputAttributes);
        }
    }

    /**
     * Records an LLM retry attempt.
     *
     * @param model Model name
     * @param errorType Error type that triggered retry
     */
    public void recordRetry(String model, String errorType) {
        Attributes attributes = Attributes.builder()
                .put("gen_ai.request.model", model != null ? model : "unknown")
                .put("error.type", errorType != null ? errorType : "unknown")
                .build();

        llmRetryCounter.add(1, attributes);
    }
}
