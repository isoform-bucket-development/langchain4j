package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Configuration for OpenTelemetry integration.
 */
public class OpenTelemetryConfig {

    private final OpenTelemetry openTelemetry;
    private final ContentCaptureConfig contentCaptureConfig;
    private final boolean enabled;
    private final boolean streamingEventsEnabled;
    private final boolean metricsEnabled;

    private OpenTelemetryConfig(Builder builder) {
        this.openTelemetry = builder.openTelemetry;
        this.contentCaptureConfig = builder.contentCaptureConfig;
        this.enabled = builder.enabled;
        this.streamingEventsEnabled = builder.streamingEventsEnabled;
        this.metricsEnabled = builder.metricsEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OpenTelemetryConfig defaultConfig() {
        return builder().build();
    }

    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    public ContentCaptureConfig contentCaptureConfig() {
        return contentCaptureConfig;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean streamingEventsEnabled() {
        return streamingEventsEnabled;
    }

    public boolean metricsEnabled() {
        return metricsEnabled;
    }

    public static class Builder {
        private OpenTelemetry openTelemetry;
        private ContentCaptureConfig contentCaptureConfig = ContentCaptureConfig.defaultConfig();
        private boolean enabled = isEnabledByEnvironment();
        private boolean streamingEventsEnabled = false;
        private boolean metricsEnabled = true;

        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        public Builder contentCaptureConfig(ContentCaptureConfig contentCaptureConfig) {
            this.contentCaptureConfig = contentCaptureConfig;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder streamingEventsEnabled(boolean streamingEventsEnabled) {
            this.streamingEventsEnabled = streamingEventsEnabled;
            return this;
        }

        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        public OpenTelemetryConfig build() {
            if (openTelemetry == null) {
                openTelemetry = io.opentelemetry.api.GlobalOpenTelemetry.get();
            }
            return new OpenTelemetryConfig(this);
        }

        private static boolean isEnabledByEnvironment() {
            String enabled = System.getenv("LANGCHAIN4J_OTEL_ENABLED");
            if (enabled == null) {
                enabled = System.getProperty("langchain4j.opentelemetry.enabled");
            }
            return enabled == null || Boolean.parseBoolean(enabled);
        }
    }
}