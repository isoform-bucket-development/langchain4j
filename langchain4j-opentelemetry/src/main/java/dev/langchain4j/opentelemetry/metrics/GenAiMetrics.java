package dev.langchain4j.opentelemetry.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;

import java.util.Arrays;
import java.util.List;

/**
 * OpenTelemetry metrics instruments for GenAI operations.
 * <p>
 * This class provides metric counters for tracking token usage following
 * the OpenTelemetry Semantic Conventions for Generative AI.
 * </p>
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI Semantic Conventions</a>
 */
public final class GenAiMetrics {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    // Metric names following OpenTelemetry GenAI semantic conventions
    /**
     * Counter for total token usage (input + output tokens combined).
     */
    public static final String TOKEN_USAGE_COUNTER_NAME = "gen_ai.client.token.usage";

    /**
     * Counter for input (prompt) tokens.
     */
    public static final String INPUT_TOKENS_COUNTER_NAME = "gen_ai.usage.input_tokens";

    /**
     * Counter for output (completion) tokens.
     */
    public static final String OUTPUT_TOKENS_COUNTER_NAME = "gen_ai.usage.output_tokens";

    /**
     * Counter for operation errors.
     */
    public static final String ERROR_COUNTER_NAME = "gen_ai.client.operation.error";

    /**
     * Histogram for operation duration in seconds.
     * Records the time taken for GenAI operations to complete.
     */
    public static final String OPERATION_DURATION_HISTOGRAM_NAME = "gen_ai.client.operation.duration";

    /**
     * Default histogram buckets covering typical LLM latencies (100ms to 30s).
     * Values are in seconds: 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0
     */
    public static final List<Double> DEFAULT_DURATION_BUCKETS = Arrays.asList(
            0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0
    );

    // Common dimension attribute keys
    /**
     * The GenAI system/provider (e.g., "openai", "anthropic").
     */
    public static final AttributeKey<String> ATTR_GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");

    /**
     * The model name used for the request.
     */
    public static final AttributeKey<String> ATTR_GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");

    /**
     * The operation name (e.g., "chat", "completion").
     */
    public static final AttributeKey<String> ATTR_GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    /**
     * The token type ("input" or "output").
     */
    public static final AttributeKey<String> ATTR_TOKEN_TYPE = AttributeKey.stringKey("gen_ai.token.type");

    /**
     * The error type for categorizing errors (e.g., "rate_limit_exceeded", "authentication_error", "timeout").
     */
    public static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");

    private final LongCounter tokenUsageCounter;
    private final LongCounter inputTokensCounter;
    private final LongCounter outputTokensCounter;
    private final LongCounter errorCounter;
    private final DoubleHistogram operationDurationHistogram;

    /**
     * Creates a new GenAiMetrics instance using the global MeterProvider.
     */
    public GenAiMetrics() {
        this(GlobalOpenTelemetry.getMeterProvider());
    }

    /**
     * Creates a new GenAiMetrics instance with the specified MeterProvider.
     *
     * @param meterProvider the meter provider to use for creating metrics
     */
    public GenAiMetrics(MeterProvider meterProvider) {
        Meter meter = meterProvider.meterBuilder(INSTRUMENTATION_NAME)
                .setInstrumentationVersion(INSTRUMENTATION_VERSION)
                .build();

        this.tokenUsageCounter = meter.counterBuilder(TOKEN_USAGE_COUNTER_NAME)
                .setDescription("Measures the number of tokens used in GenAI operations")
                .setUnit("{token}")
                .build();

        this.inputTokensCounter = meter.counterBuilder(INPUT_TOKENS_COUNTER_NAME)
                .setDescription("Measures the number of input (prompt) tokens used")
                .setUnit("{token}")
                .build();

        this.outputTokensCounter = meter.counterBuilder(OUTPUT_TOKENS_COUNTER_NAME)
                .setDescription("Measures the number of output (completion) tokens generated")
                .setUnit("{token}")
                .build();

        this.errorCounter = meter.counterBuilder(ERROR_COUNTER_NAME)
                .setDescription("Counts the number of errors in GenAI operations")
                .setUnit("{error}")
                .build();

        this.operationDurationHistogram = meter.histogramBuilder(OPERATION_DURATION_HISTOGRAM_NAME)
                .setDescription("Measures the duration of GenAI operations in seconds")
                .setUnit("s")
                .setExplicitBucketBoundariesAdvice(DEFAULT_DURATION_BUCKETS)
                .build();
    }

    /**
     * Returns the total token usage counter.
     *
     * @return the token usage counter
     */
    public LongCounter getTokenUsageCounter() {
        return tokenUsageCounter;
    }

    /**
     * Returns the input tokens counter.
     *
     * @return the input tokens counter
     */
    public LongCounter getInputTokensCounter() {
        return inputTokensCounter;
    }

    /**
     * Returns the output tokens counter.
     *
     * @return the output tokens counter
     */
    public LongCounter getOutputTokensCounter() {
        return outputTokensCounter;
    }

    /**
     * Returns the error counter.
     *
     * @return the error counter
     */
    public LongCounter getErrorCounter() {
        return errorCounter;
    }

    /**
     * Returns the operation duration histogram.
     *
     * @return the operation duration histogram
     */
    public DoubleHistogram getOperationDurationHistogram() {
        return operationDurationHistogram;
    }

    /**
     * Creates a new builder for GenAiMetrics.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating GenAiMetrics instances.
     */
    public static class Builder {
        private MeterProvider meterProvider = GlobalOpenTelemetry.getMeterProvider();

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
         * Builds the GenAiMetrics instance.
         *
         * @return the configured GenAiMetrics
         */
        public GenAiMetrics build() {
            return new GenAiMetrics(meterProvider);
        }
    }
}
