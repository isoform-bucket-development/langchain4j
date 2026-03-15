package dev.langchain4j.opentelemetry.spring;

import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for OpenTelemetry LangChain4j integration.
 * <p>
 * These properties allow customization of the OpenTelemetry instrumentation
 * behavior through application.properties or application.yml files.
 * </p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * langchain4j.opentelemetry.enabled=true
 * langchain4j.opentelemetry.tracing.enabled=true
 * langchain4j.opentelemetry.metrics.enabled=true
 * langchain4j.opentelemetry.capture-content=true
 * langchain4j.opentelemetry.content-capture-mode=FULL
 * langchain4j.opentelemetry.sampling-rate=1.0
 * </pre>
 */
@ConfigurationProperties(prefix = "langchain4j.opentelemetry")
public class OpenTelemetryProperties {

    /**
     * Whether OpenTelemetry instrumentation is enabled overall.
     * When set to false, no instrumentation beans are created.
     */
    private boolean enabled = true;

    /**
     * Tracing-specific configuration.
     */
    private TracingProperties tracing = new TracingProperties();

    /**
     * Metrics-specific configuration.
     */
    private MetricsProperties metrics = new MetricsProperties();

    /**
     * Whether to capture message content in spans.
     * When false, only metadata (model, tokens, duration) is captured.
     * This is a convenience property that maps to content-capture-mode.
     */
    private boolean captureContent = true;

    /**
     * The content capture mode for controlling what data is recorded.
     */
    private ContentCaptureMode contentCaptureMode = ContentCaptureMode.FULL;

    /**
     * The sampling rate for traces (0.0 to 1.0).
     * A value of 1.0 means all traces are recorded, 0.5 means 50% are sampled.
     */
    private double samplingRate = 1.0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TracingProperties getTracing() {
        return tracing;
    }

    public void setTracing(TracingProperties tracing) {
        this.tracing = tracing;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public boolean isCaptureContent() {
        return captureContent;
    }

    public void setCaptureContent(boolean captureContent) {
        this.captureContent = captureContent;
    }

    public ContentCaptureMode getContentCaptureMode() {
        return contentCaptureMode;
    }

    public void setContentCaptureMode(ContentCaptureMode contentCaptureMode) {
        this.contentCaptureMode = contentCaptureMode;
    }

    public double getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(double samplingRate) {
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0");
        }
        this.samplingRate = samplingRate;
    }

    /**
     * Computes the effective content capture mode based on both captureContent
     * and contentCaptureMode properties. If captureContent is explicitly set to false,
     * it takes precedence and returns NONE.
     *
     * @return the effective content capture mode
     */
    public ContentCaptureMode getEffectiveContentCaptureMode() {
        if (!captureContent) {
            return ContentCaptureMode.NONE;
        }
        return contentCaptureMode;
    }

    /**
     * Tracing-specific configuration properties.
     */
    public static class TracingProperties {

        /**
         * Whether tracing is enabled.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Metrics-specific configuration properties.
     */
    public static class MetricsProperties {

        /**
         * Whether metrics collection is enabled.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
