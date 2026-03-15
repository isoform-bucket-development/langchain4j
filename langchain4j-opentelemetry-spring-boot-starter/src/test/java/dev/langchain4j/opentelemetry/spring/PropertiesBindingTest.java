package dev.langchain4j.opentelemetry.spring;

import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for property binding in OpenTelemetryProperties.
 */
class PropertiesBindingTest {

    @SpringBootTest(classes = DefaultPropertiesTest.TestConfig.class)
    @EnableConfigurationProperties(OpenTelemetryProperties.class)
    static class DefaultPropertiesTest {

        @Autowired
        private OpenTelemetryProperties properties;

        @Test
        @DisplayName("Default properties are applied when no configuration provided")
        void defaultPropertiesApplied() {
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getTracing().isEnabled()).isTrue();
            assertThat(properties.getMetrics().isEnabled()).isTrue();
            assertThat(properties.isCaptureContent()).isTrue();
            assertThat(properties.getContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);
            assertThat(properties.getSamplingRate()).isEqualTo(1.0);
            assertThat(properties.getEffectiveContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestConfig {
        }
    }

    @SpringBootTest(classes = CaptureContentDisabledTest.TestConfig.class)
    @EnableConfigurationProperties(OpenTelemetryProperties.class)
    @TestPropertySource(properties = "langchain4j.opentelemetry.capture-content=false")
    static class CaptureContentDisabledTest {

        @Autowired
        private OpenTelemetryProperties properties;

        @Test
        @DisplayName("capture-content=false sets effective mode to NONE")
        void captureContentDisabled() {
            assertThat(properties.isCaptureContent()).isFalse();
            assertThat(properties.getEffectiveContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestConfig {
        }
    }

    @SpringBootTest(classes = MetadataCaptureModeTest.TestConfig.class)
    @EnableConfigurationProperties(OpenTelemetryProperties.class)
    @TestPropertySource(properties = "langchain4j.opentelemetry.content-capture-mode=METADATA")
    static class MetadataCaptureModeTest {

        @Autowired
        private OpenTelemetryProperties properties;

        @Test
        @DisplayName("content-capture-mode=METADATA is properly bound")
        void metadataCaptureMode() {
            assertThat(properties.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
            assertThat(properties.getEffectiveContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestConfig {
        }
    }

    @SpringBootTest(classes = AllPropertiesConfiguredTest.TestConfig.class)
    @EnableConfigurationProperties(OpenTelemetryProperties.class)
    @TestPropertySource(properties = {
            "langchain4j.opentelemetry.enabled=true",
            "langchain4j.opentelemetry.tracing.enabled=false",
            "langchain4j.opentelemetry.metrics.enabled=false",
            "langchain4j.opentelemetry.capture-content=true",
            "langchain4j.opentelemetry.content-capture-mode=NONE",
            "langchain4j.opentelemetry.sampling-rate=0.1"
    })
    static class AllPropertiesConfiguredTest {

        @Autowired
        private OpenTelemetryProperties properties;

        @Test
        @DisplayName("All properties are correctly bound from configuration")
        void allPropertiesBound() {
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getTracing().isEnabled()).isFalse();
            assertThat(properties.getMetrics().isEnabled()).isFalse();
            assertThat(properties.isCaptureContent()).isTrue();
            assertThat(properties.getContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
            assertThat(properties.getSamplingRate()).isEqualTo(0.1);
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestConfig {
        }
    }

    @Test
    @DisplayName("OpenTelemetryProperties validates sampling rate range")
    void samplingRateValidation() {
        OpenTelemetryProperties properties = new OpenTelemetryProperties();

        // Valid values
        properties.setSamplingRate(0.0);
        assertThat(properties.getSamplingRate()).isEqualTo(0.0);

        properties.setSamplingRate(1.0);
        assertThat(properties.getSamplingRate()).isEqualTo(1.0);

        properties.setSamplingRate(0.5);
        assertThat(properties.getSamplingRate()).isEqualTo(0.5);

        // Invalid values
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                properties.setSamplingRate(-0.1));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                properties.setSamplingRate(1.1));
    }

    @Test
    @DisplayName("capture-content=false takes precedence over content-capture-mode")
    void captureContentPrecedence() {
        OpenTelemetryProperties properties = new OpenTelemetryProperties();

        // Set content-capture-mode to FULL
        properties.setContentCaptureMode(ContentCaptureMode.FULL);
        assertThat(properties.getEffectiveContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);

        // Now disable capture-content - should override to NONE
        properties.setCaptureContent(false);
        assertThat(properties.getEffectiveContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
    }
}
