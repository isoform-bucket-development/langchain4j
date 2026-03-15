package dev.langchain4j.opentelemetry;

import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import dev.langchain4j.opentelemetry.metrics.GenAiMetrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;

/**
 * Main entry point for configuring OpenTelemetry instrumentation in plain Java.
 * <p>
 * This class provides a fluent builder API for creating OpenTelemetry-instrumented
 * listeners that can be registered with LangChain4j ChatModel and AiService instances.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create with defaults (uses GlobalOpenTelemetry)
 * OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder().build();
 *
 * // Create with custom providers
 * OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
 *     .tracerProvider(customTracerProvider)
 *     .meterProvider(customMeterProvider)
 *     .contentCaptureMode(ContentCaptureMode.METADATA)
 *     .build();
 *
 * // Register listener with ChatModel
 * ChatModel model = OpenAiChatModel.builder()
 *     .apiKey(apiKey)
 *     .listeners(List.of(otel.createChatModelListener()))
 *     .build();
 * }</pre>
 *
 * @see OpenTelemetryChatModelListener
 * @see OpenTelemetryLangChain4jConfig
 */
public final class OpenTelemetryLangChain4j {

    private final TracerProvider tracerProvider;
    private final MeterProvider meterProvider;
    private final OpenTelemetryLangChain4jConfig config;
    private final GenAiMetrics metrics;

    private OpenTelemetryLangChain4j(Builder builder) {
        this.tracerProvider = builder.tracerProvider;
        this.meterProvider = builder.meterProvider;
        this.config = builder.config;
        this.metrics = new GenAiMetrics(meterProvider);
    }

    /**
     * Creates a new builder for configuring OpenTelemetry instrumentation.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a ChatModelListener configured with this instance's settings.
     * <p>
     * The returned listener can be registered with any ChatModel via the
     * {@code listeners()} method in the ChatModel builder.
     * </p>
     *
     * @return a new ChatModelListener instance
     */
    public OpenTelemetryChatModelListener createChatModelListener() {
        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(tracerProvider)
                .config(config)
                .build();
    }

    /**
     * Creates a streaming ChatModelListener configured with this instance's settings.
     * <p>
     * The returned listener is optimized for streaming ChatModel operations
     * with proper context propagation across async callbacks.
     * </p>
     *
     * @return a new streaming ChatModelListener instance
     */
    public OpenTelemetryChatModelListener createStreamingChatModelListener() {
        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(tracerProvider)
                .streaming(true)
                .config(config)
                .build();
    }

    /**
     * Returns the TracerProvider used by this instance.
     *
     * @return the tracer provider
     */
    public TracerProvider getTracerProvider() {
        return tracerProvider;
    }

    /**
     * Returns the MeterProvider used by this instance.
     *
     * @return the meter provider
     */
    public MeterProvider getMeterProvider() {
        return meterProvider;
    }

    /**
     * Returns the configuration used by this instance.
     *
     * @return the configuration
     */
    public OpenTelemetryLangChain4jConfig getConfig() {
        return config;
    }

    /**
     * Returns the GenAiMetrics instance for recording token usage and other metrics.
     *
     * @return the metrics instance
     */
    public GenAiMetrics getMetrics() {
        return metrics;
    }

    /**
     * Returns whether tracing is enabled.
     *
     * @return true if tracing is enabled
     */
    public boolean isTracingEnabled() {
        return config.isTracingEnabled();
    }

    /**
     * Returns whether metrics collection is enabled.
     *
     * @return true if metrics are enabled
     */
    public boolean isMetricsEnabled() {
        return config.isMetricsEnabled();
    }

    /**
     * Returns the content capture mode.
     *
     * @return the content capture mode
     */
    public ContentCaptureMode getContentCaptureMode() {
        return config.getContentCaptureMode();
    }

    /**
     * Builder for creating {@link OpenTelemetryLangChain4j} instances.
     * <p>
     * By default, the builder uses the global OpenTelemetry providers
     * and enables all features (tracing, metrics, content capture).
     * </p>
     */
    public static final class Builder {

        private TracerProvider tracerProvider = GlobalOpenTelemetry.getTracerProvider();
        private MeterProvider meterProvider = GlobalOpenTelemetry.getMeterProvider();
        private OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.defaultConfig();

        private Builder() {}

        /**
         * Sets the TracerProvider to use for creating spans.
         * <p>
         * If not set, uses {@link GlobalOpenTelemetry#getTracerProvider()}.
         * </p>
         *
         * @param tracerProvider the tracer provider to use
         * @return this builder
         */
        public Builder tracerProvider(TracerProvider tracerProvider) {
            this.tracerProvider = tracerProvider != null
                    ? tracerProvider
                    : GlobalOpenTelemetry.getTracerProvider();
            return this;
        }

        /**
         * Sets the MeterProvider to use for recording metrics.
         * <p>
         * If not set, uses {@link GlobalOpenTelemetry#getMeterProvider()}.
         * </p>
         *
         * @param meterProvider the meter provider to use
         * @return this builder
         */
        public Builder meterProvider(MeterProvider meterProvider) {
            this.meterProvider = meterProvider != null
                    ? meterProvider
                    : GlobalOpenTelemetry.getMeterProvider();
            return this;
        }

        /**
         * Sets the complete configuration for OpenTelemetry instrumentation.
         * <p>
         * This method allows setting all configuration options at once.
         * For individual settings, use the specific builder methods.
         * </p>
         *
         * @param config the configuration to use
         * @return this builder
         */
        public Builder config(OpenTelemetryLangChain4jConfig config) {
            this.config = config != null ? config : OpenTelemetryLangChain4jConfig.defaultConfig();
            return this;
        }

        /**
         * Enables or disables tracing.
         *
         * @param enabled true to enable tracing, false to disable
         * @return this builder
         */
        public Builder tracingEnabled(boolean enabled) {
            this.config = OpenTelemetryLangChain4jConfig.builder()
                    .tracingEnabled(enabled)
                    .metricsEnabled(config.isMetricsEnabled())
                    .contentCaptureMode(config.getContentCaptureMode())
                    .samplingRate(config.getSamplingRate())
                    .build();
            return this;
        }

        /**
         * Enables or disables metrics collection.
         *
         * @param enabled true to enable metrics, false to disable
         * @return this builder
         */
        public Builder metricsEnabled(boolean enabled) {
            this.config = OpenTelemetryLangChain4jConfig.builder()
                    .tracingEnabled(config.isTracingEnabled())
                    .metricsEnabled(enabled)
                    .contentCaptureMode(config.getContentCaptureMode())
                    .samplingRate(config.getSamplingRate())
                    .build();
            return this;
        }

        /**
         * Sets the content capture mode.
         *
         * @param mode the content capture mode
         * @return this builder
         */
        public Builder contentCaptureMode(ContentCaptureMode mode) {
            this.config = OpenTelemetryLangChain4jConfig.builder()
                    .tracingEnabled(config.isTracingEnabled())
                    .metricsEnabled(config.isMetricsEnabled())
                    .contentCaptureMode(mode)
                    .samplingRate(config.getSamplingRate())
                    .build();
            return this;
        }

        /**
         * Sets the sampling rate for traces.
         *
         * @param rate the sampling rate (0.0 to 1.0)
         * @return this builder
         * @throws IllegalArgumentException if rate is not between 0.0 and 1.0
         */
        public Builder samplingRate(double rate) {
            this.config = OpenTelemetryLangChain4jConfig.builder()
                    .tracingEnabled(config.isTracingEnabled())
                    .metricsEnabled(config.isMetricsEnabled())
                    .contentCaptureMode(config.getContentCaptureMode())
                    .samplingRate(rate)
                    .build();
            return this;
        }

        /**
         * Builds the OpenTelemetryLangChain4j instance.
         *
         * @return a new OpenTelemetryLangChain4j instance configured with the builder settings
         */
        public OpenTelemetryLangChain4j build() {
            return new OpenTelemetryLangChain4j(this);
        }
    }
}
