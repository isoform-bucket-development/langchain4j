package dev.langchain4j.opentelemetry.spring;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for OpenTelemetry LangChain4j integration.
 * <p>
 * This auto-configuration provides zero-config setup for OpenTelemetry instrumentation
 * of LangChain4j operations. It automatically registers listener beans when:
 * </p>
 * <ul>
 *   <li>OpenTelemetry SDK is on the classpath</li>
 *   <li>LangChain4j core is on the classpath (ChatModelListener available)</li>
 *   <li>No custom listener bean is already defined</li>
 *   <li>The property langchain4j.opentelemetry.enabled is true (default)</li>
 * </ul>
 *
 * <p>Configuration can be customized via application.properties:</p>
 * <pre>
 * langchain4j.opentelemetry.enabled=true
 * langchain4j.opentelemetry.tracing.enabled=true
 * langchain4j.opentelemetry.metrics.enabled=true
 * langchain4j.opentelemetry.capture-content=true
 * langchain4j.opentelemetry.content-capture-mode=FULL
 * langchain4j.opentelemetry.sampling-rate=1.0
 * </pre>
 *
 * <p>Users can override the auto-configured beans by defining their own:</p>
 * <pre>
 * &#64;Bean
 * public OpenTelemetryChatModelListener customListener() {
 *     return OpenTelemetryChatModelListener.builder()
 *         .config(OpenTelemetryLangChain4jConfig.builder()
 *             .tracingEnabled(true)
 *             .contentCaptureMode(ContentCaptureMode.METADATA)
 *             .build())
 *         .build();
 * }
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass({OpenTelemetry.class, ChatModelListener.class})
@ConditionalOnProperty(prefix = "langchain4j.opentelemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenTelemetryProperties.class)
public class OpenTelemetryAutoConfiguration {

    /**
     * Creates the OpenTelemetry configuration from Spring Boot properties.
     *
     * @param properties the OpenTelemetry properties
     * @return the configured OpenTelemetryLangChain4jConfig
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetryLangChain4jConfig openTelemetryLangChain4jConfig(OpenTelemetryProperties properties) {
        return OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(properties.getTracing().isEnabled())
                .metricsEnabled(properties.getMetrics().isEnabled())
                .contentCaptureMode(properties.getEffectiveContentCaptureMode())
                .samplingRate(properties.getSamplingRate())
                .build();
    }

    /**
     * Creates the OpenTelemetryChatModelListener bean for tracing chat model operations.
     * <p>
     * This bean is only created if:
     * <ul>
     *   <li>No existing OpenTelemetryChatModelListener bean is defined</li>
     *   <li>OpenTelemetry SDK is available on the classpath</li>
     * </ul>
     *
     * @param config the OpenTelemetry configuration
     * @param tracerProvider the tracer provider (optional, uses GlobalOpenTelemetry if not available)
     * @return the configured OpenTelemetryChatModelListener
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetryChatModelListener.class)
    public OpenTelemetryChatModelListener openTelemetryChatModelListener(
            OpenTelemetryLangChain4jConfig config,
            TracerProvider tracerProvider) {
        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(tracerProvider)
                .config(config)
                .build();
    }

    /**
     * Creates a TracerProvider bean from GlobalOpenTelemetry if none is available.
     * This allows the auto-configuration to work even when OpenTelemetry is configured
     * via the Java agent or other means.
     *
     * @return the TracerProvider from GlobalOpenTelemetry
     */
    @Bean
    @ConditionalOnMissingBean(TracerProvider.class)
    public TracerProvider tracerProvider() {
        return GlobalOpenTelemetry.getTracerProvider();
    }
}
