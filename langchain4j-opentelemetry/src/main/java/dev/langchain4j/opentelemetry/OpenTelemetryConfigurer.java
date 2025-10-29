package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/**
 * Configures OpenTelemetry SDK with autoconfiguration support for OTEL_* environment variables.
 * <p>
 * This class delegates to {@link AutoConfiguredOpenTelemetrySdk#initialize()} for full support
 * of OpenTelemetry environment variable configuration as per the OpenTelemetry specification:
 * - OTEL_SERVICE_NAME: Service name
 * - OTEL_EXPORTER_OTLP_ENDPOINT: OTLP exporter endpoint
 * - OTEL_TRACES_SAMPLER: Trace sampler type
 * - OTEL_TRACES_SAMPLER_ARG: Trace sampler argument
 * - OTEL_METRICS_EXPORTER: Metrics exporter (defaults to OTLP)
 * - And many more as per OpenTelemetry specification
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * OpenTelemetry openTelemetry = OpenTelemetryConfigurer.configure();
 * }</pre>
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryConfigurer {

    private OpenTelemetryConfigurer() {
        // Utility class
    }

    /**
     * Initializes OpenTelemetry SDK using autoconfiguration from environment variables
     * and system properties. This method respects all OTEL_* environment variables
     * as defined in the OpenTelemetry specification.
     * <p>
     * Default behavior:
     * - OTLP exporter is used for traces and metrics
     * - Service name defaults to "unknown_service:java"
     * - Traces and metrics are exported to http://localhost:4317 (OTLP/gRPC)
     * or http://localhost:4318 (OTLP/HTTP) depending on protocol
     * </p>
     *
     * @return configured OpenTelemetry instance
     */
    public static OpenTelemetry configure() {
        return AutoConfiguredOpenTelemetrySdk.initialize()
                .getOpenTelemetrySdk();
    }

    /**
     * Returns a global OpenTelemetry instance. This method is useful when you want
     * to use the same OpenTelemetry instance across multiple components.
     * <p>
     * Note: This method returns the global instance configured by the OpenTelemetry SDK.
     * If no global instance has been configured, it returns a no-op instance.
     * </p>
     *
     * @return global OpenTelemetry instance
     */
    public static OpenTelemetry getGlobalOpenTelemetry() {
        return io.opentelemetry.api.GlobalOpenTelemetry.get();
    }
}
