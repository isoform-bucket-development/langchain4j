package dev.langchain4j.opentelemetry;

import java.util.*;

/**
 * Configuration for OpenTelemetry integration with LangChain4j.
 *
 * This class implements:
 * - REQ-5: Configuration Options (enable/disable tracing, metrics, sampling rates)
 * - REQ-7: Content Capture Options (tiered content capture levels)
 * - NFR-5: Security and Privacy (privacy-first defaults)
 * - NFR-6: Extensibility (custom attributes)
 *
 * Provides sensible privacy-first defaults:
 * - Content capture: NONE (no message content)
 * - Tracing: enabled
 * - Metrics: enabled
 * - Sampling rate: 1.0 (100%)
 */
public class OpenTelemetryConfig {

    private final boolean captureMessageContent;
    private final boolean tracingEnabled;
    private final boolean metricsEnabled;
    private final double samplingRate;
    private final ContentCaptureMode contentCaptureMode;
    private final Set<String> excludedAttributes;
    private final Map<String, String> customAttributes;

    private OpenTelemetryConfig(Builder builder) {
        this.captureMessageContent = builder.captureMessageContent;
        this.tracingEnabled = builder.tracingEnabled;
        this.metricsEnabled = builder.metricsEnabled;
        this.samplingRate = builder.samplingRate;
        this.contentCaptureMode = builder.contentCaptureMode;
        this.excludedAttributes = Collections.unmodifiableSet(new HashSet<>(builder.excludedAttributes));
        this.customAttributes = Collections.unmodifiableMap(new HashMap<>(builder.customAttributes));
    }

    public boolean isCaptureMessageContent() {
        return captureMessageContent;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public double getSamplingRate() {
        return samplingRate;
    }

    public ContentCaptureMode getContentCaptureMode() {
        return contentCaptureMode;
    }

    public Set<String> getExcludedAttributes() {
        return excludedAttributes;
    }

    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean captureMessageContent = false;  // Privacy-first default
        private boolean tracingEnabled = true;           // Observability enabled by default
        private boolean metricsEnabled = true;           // Observability enabled by default
        private double samplingRate = 1.0;               // 100% sampling by default
        private ContentCaptureMode contentCaptureMode = ContentCaptureMode.NONE;  // Privacy-first
        private Set<String> excludedAttributes = new HashSet<>();
        private Map<String, String> customAttributes = new HashMap<>();

        private Builder() {
        }

        /**
         * Enable or disable message content capture.
         * Default: false (privacy-first)
         *
         * WARNING: Enabling this may capture PII and sensitive data in traces.
         * Consider using contentCaptureMode(ContentCaptureMode) for finer control.
         */
        public Builder captureMessageContent(boolean captureMessageContent) {
            this.captureMessageContent = captureMessageContent;
            // Automatically set content capture mode based on this flag
            if (captureMessageContent) {
                this.contentCaptureMode = ContentCaptureMode.FULL;
            }
            return this;
        }

        /**
         * Enable or disable OpenTelemetry tracing.
         * Default: true
         */
        public Builder tracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
            return this;
        }

        /**
         * Enable or disable OpenTelemetry metrics collection.
         * Default: true
         */
        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        /**
         * Set sampling rate for traces.
         * Must be between 0.0 (no sampling) and 1.0 (100% sampling).
         * Default: 1.0
         *
         * @throws IllegalArgumentException if samplingRate is not between 0.0 and 1.0
         */
        public Builder samplingRate(double samplingRate) {
            if (samplingRate < 0.0 || samplingRate > 1.0) {
                throw new IllegalArgumentException("Sampling rate must be between 0.0 and 1.0, got: " + samplingRate);
            }
            this.samplingRate = samplingRate;
            return this;
        }

        /**
         * Set content capture mode for fine-grained control.
         * Default: ContentCaptureMode.NONE (privacy-first)
         *
         * Options:
         * - NONE: Only metadata (model, tokens, duration)
         * - METADATA: Include message roles and tool names (no content)
         * - FULL: Capture complete messages (WARNING: may capture PII)
         */
        public Builder contentCaptureMode(ContentCaptureMode contentCaptureMode) {
            this.contentCaptureMode = contentCaptureMode;
            this.captureMessageContent = (contentCaptureMode == ContentCaptureMode.FULL);
            return this;
        }

        /**
         * Exclude specific attributes from spans for privacy.
         * Can be called multiple times to exclude multiple attributes.
         */
        public Builder excludeAttribute(String attributeName) {
            this.excludedAttributes.add(attributeName);
            return this;
        }

        /**
         * Add custom attribute to all spans (NFR-6: Extensibility).
         * Can be called multiple times to add multiple attributes.
         */
        public Builder addCustomAttribute(String key, String value) {
            this.customAttributes.put(key, value);
            return this;
        }

        /**
         * Build the OpenTelemetryConfig instance.
         * Validates all configuration values.
         *
         * @throws IllegalArgumentException if configuration is invalid
         */
        public OpenTelemetryConfig build() {
            // Validation is already done in setters
            return new OpenTelemetryConfig(this);
        }
    }
}
