package dev.langchain4j.opentelemetry.spring;

import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenTelemetry Spring Boot auto-configuration.
 */
class AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    @DisplayName("Test case 1: Default auto-configuration registers OpenTelemetryChatModelListener bean")
    void defaultAutoConfigurationRegistersBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenTelemetryChatModelListener.class);
            assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);
            assertThat(context).hasSingleBean(TracerProvider.class);

            OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
            assertThat(config.isTracingEnabled()).isTrue();
            assertThat(config.isMetricsEnabled()).isTrue();
            assertThat(config.getContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);
            assertThat(config.getSamplingRate()).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("Test case 2: Property capture-content=false disables content capture")
    void propertyDisablesContentCapture() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.capture-content=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.getContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
                });
    }

    @Test
    @DisplayName("Test case 2b: Property content-capture-mode=METADATA sets metadata mode")
    void propertySetsCaptureMode() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.content-capture-mode=METADATA")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
                });
    }

    @Test
    @DisplayName("Test case 2c: Property tracing.enabled=false disables tracing")
    void propertyDisablesTracing() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.tracing.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.isTracingEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("Test case 2d: Property metrics.enabled=false disables metrics")
    void propertyDisablesMetrics() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.isMetricsEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("Test case 2e: Property sampling-rate=0.5 sets 50% sampling")
    void propertySetsConfiguredSamplingRate() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.sampling-rate=0.5")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.getSamplingRate()).isEqualTo(0.5);
                });
    }

    @Test
    @DisplayName("Test case 3: Auto-configuration skipped when OTel SDK not on classpath")
    void autoConfigurationSkippedWithoutOpenTelemetry() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(io.opentelemetry.api.OpenTelemetry.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenTelemetryChatModelListener.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryLangChain4jConfig.class);
                });
    }

    @Test
    @DisplayName("Test case 3b: Auto-configuration skipped when langchain4j.opentelemetry.enabled=false")
    void autoConfigurationSkippedWhenDisabled() {
        contextRunner
                .withPropertyValues("langchain4j.opentelemetry.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenTelemetryChatModelListener.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryLangChain4jConfig.class);
                });
    }

    @Test
    @DisplayName("Test case 4: User-defined listener bean takes precedence over auto-configured")
    void customBeanTakesPrecedence() {
        contextRunner
                .withUserConfiguration(CustomListenerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryChatModelListener.class);

                    OpenTelemetryChatModelListener listener = context.getBean(OpenTelemetryChatModelListener.class);
                    OpenTelemetryLangChain4jConfig config = listener.getConfig();

                    // Custom listener uses METADATA mode
                    assertThat(config.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
                });
    }

    @Test
    @DisplayName("Test case 4b: User-defined config bean takes precedence")
    void customConfigBeanTakesPrecedence() {
        contextRunner
                .withUserConfiguration(CustomConfigConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetryLangChain4jConfig.class);

                    OpenTelemetryLangChain4jConfig config = context.getBean(OpenTelemetryLangChain4jConfig.class);
                    assertThat(config.getSamplingRate()).isEqualTo(0.25);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomListenerConfiguration {

        @Bean
        OpenTelemetryChatModelListener customListener() {
            return OpenTelemetryChatModelListener.builder()
                    .config(OpenTelemetryLangChain4jConfig.builder()
                            .contentCaptureMode(ContentCaptureMode.METADATA)
                            .build())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConfigConfiguration {

        @Bean
        OpenTelemetryLangChain4jConfig customConfig() {
            return OpenTelemetryLangChain4jConfig.builder()
                    .samplingRate(0.25)
                    .build();
        }
    }
}
