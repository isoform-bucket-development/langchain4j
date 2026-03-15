package dev.langchain4j.opentelemetry.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.MeterProvider;

/**
 * Records token usage metrics from LLM responses.
 * <p>
 * This class provides a convenient API for recording token usage metrics
 * with proper dimensions following OpenTelemetry semantic conventions.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TokenUsageRecorder recorder = TokenUsageRecorder.builder()
 *     .meterProvider(sdkMeterProvider)
 *     .build();
 *
 * recorder.recordTokenUsage(50, 100, "openai", "gpt-4o", "chat");
 * }</pre>
 */
public class TokenUsageRecorder {

    private final GenAiMetrics metrics;

    /**
     * Creates a new TokenUsageRecorder with the specified GenAiMetrics.
     *
     * @param metrics the GenAiMetrics instance to use for recording
     */
    public TokenUsageRecorder(GenAiMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Creates a new TokenUsageRecorder using the global MeterProvider.
     */
    public TokenUsageRecorder() {
        this(new GenAiMetrics());
    }

    /**
     * Records token usage with the specified dimensions.
     *
     * @param inputTokens   the number of input (prompt) tokens
     * @param outputTokens  the number of output (completion) tokens
     * @param system        the GenAI system/provider (e.g., "openai", "anthropic")
     * @param model         the model name (e.g., "gpt-4o", "claude-3-sonnet")
     * @param operationName the operation name (e.g., "chat", "completion")
     */
    public void recordTokenUsage(long inputTokens, long outputTokens,
                                  String system, String model, String operationName) {
        Attributes attributes = buildAttributes(system, model, operationName);
        recordTokenUsage(inputTokens, outputTokens, attributes);
    }

    /**
     * Records token usage with the specified attributes.
     *
     * @param inputTokens  the number of input (prompt) tokens
     * @param outputTokens the number of output (completion) tokens
     * @param attributes   the attributes/dimensions for the metrics
     */
    public void recordTokenUsage(long inputTokens, long outputTokens, Attributes attributes) {
        // Record total token usage
        long totalTokens = inputTokens + outputTokens;
        metrics.getTokenUsageCounter().add(totalTokens, attributes);

        // Record separate input tokens counter
        if (inputTokens > 0) {
            metrics.getInputTokensCounter().add(inputTokens, attributes);
        }

        // Record separate output tokens counter
        if (outputTokens > 0) {
            metrics.getOutputTokensCounter().add(outputTokens, attributes);
        }
    }

    /**
     * Builds attributes from the provided dimension values.
     *
     * @param system        the GenAI system/provider
     * @param model         the model name
     * @param operationName the operation name
     * @return the built attributes
     */
    private Attributes buildAttributes(String system, String model, String operationName) {
        AttributesBuilder builder = Attributes.builder();

        if (system != null) {
            builder.put(GenAiMetrics.ATTR_GEN_AI_SYSTEM, system);
        }

        if (model != null) {
            builder.put(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL, model);
        }

        if (operationName != null) {
            builder.put(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME, operationName);
        }

        return builder.build();
    }

    /**
     * Returns the underlying GenAiMetrics instance.
     *
     * @return the GenAiMetrics instance
     */
    public GenAiMetrics getMetrics() {
        return metrics;
    }

    /**
     * Creates a new builder for TokenUsageRecorder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating TokenUsageRecorder instances.
     */
    public static class Builder {
        private MeterProvider meterProvider;

        /**
         * Sets the meter provider to use.
         *
         * @param meterProvider the meter provider
         * @return this builder
         */
        public Builder meterProvider(MeterProvider meterProvider) {
            this.meterProvider = meterProvider;
            return this;
        }

        /**
         * Builds the TokenUsageRecorder instance.
         *
         * @return the configured TokenUsageRecorder
         */
        public TokenUsageRecorder build() {
            GenAiMetrics genAiMetrics;
            if (meterProvider != null) {
                genAiMetrics = GenAiMetrics.builder()
                        .meterProvider(meterProvider)
                        .build();
            } else {
                genAiMetrics = new GenAiMetrics();
            }
            return new TokenUsageRecorder(genAiMetrics);
        }
    }
}
