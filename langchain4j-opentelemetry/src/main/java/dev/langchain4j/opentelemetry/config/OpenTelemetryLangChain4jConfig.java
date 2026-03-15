package dev.langchain4j.opentelemetry.config;

/**
 * Configuration holder for OpenTelemetry LangChain4j integration.
 * Uses a builder pattern for fluent configuration.
 */
public final class OpenTelemetryLangChain4jConfig {

    private final boolean tracingEnabled;
    private final boolean metricsEnabled;
    private final ContentCaptureMode contentCaptureMode;
    private final double samplingRate;

    private OpenTelemetryLangChain4jConfig(Builder builder) {
        this.tracingEnabled = builder.tracingEnabled;
        this.metricsEnabled = builder.metricsEnabled;
        this.contentCaptureMode = builder.contentCaptureMode;
        this.samplingRate = builder.samplingRate;
    }

    /**
     * Creates a new builder for configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default configuration with all features enabled.
     *
     * @return a default configuration instance
     */
    public static OpenTelemetryLangChain4jConfig defaultConfig() {
        return builder().build();
    }

    /**
     * Returns whether tracing is enabled.
     */
    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    /**
     * Returns whether metrics collection is enabled.
     */
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Returns the content capture mode.
     */
    public ContentCaptureMode getContentCaptureMode() {
        return contentCaptureMode;
    }

    /**
     * Returns the sampling rate (0.0 to 1.0).
     */
    public double getSamplingRate() {
        return samplingRate;
    }

    /**
     * Builder for {@link OpenTelemetryLangChain4jConfig}.
     */
    public static final class Builder {

        private boolean tracingEnabled = true;
        private boolean metricsEnabled = true;
        private ContentCaptureMode contentCaptureMode = ContentCaptureMode.FULL;
        private double samplingRate = 1.0;

        private Builder() {}

        /**
         * Enables or disables tracing.
         *
         * @param enabled true to enable tracing, false to disable
         * @return this builder
         */
        public Builder tracingEnabled(boolean enabled) {
            this.tracingEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables metrics collection.
         *
         * @param enabled true to enable metrics, false to disable
         * @return this builder
         */
        public Builder metricsEnabled(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        /**
         * Sets the content capture mode.
         *
         * @param mode the content capture mode
         * @return this builder
         */
        public Builder contentCaptureMode(ContentCaptureMode mode) {
            this.contentCaptureMode = mode != null ? mode : ContentCaptureMode.FULL;
            return this;
        }

        /**
         * Sets the sampling rate for traces.
         *
         * @param rate the sampling rate (0.0 to 1.0)
         * @return this builder
         */
        public Builder samplingRate(double rate) {
            if (rate < 0.0 || rate > 1.0) {
                throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0");
            }
            this.samplingRate = rate;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the built configuration
         */
        public OpenTelemetryLangChain4jConfig build() {
            return new OpenTelemetryLangChain4jConfig(this);
        }
    }
}
