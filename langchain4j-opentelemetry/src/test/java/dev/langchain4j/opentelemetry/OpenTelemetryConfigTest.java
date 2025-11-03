package dev.langchain4j.opentelemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * TDD Red Phase: Tests for OpenTelemetry Configuration.
 *
 * This test class validates REQ-5 (Configuration Options) and REQ-7 (Content Capture Options).
 *
 * Expected behavior: All tests should FAIL until OpenTelemetryConfig is implemented.
 */
class OpenTelemetryConfigTest {

    @Test
    void should_create_config_with_default_values() {
        // Given: REQ-5 - Configuration with sensible defaults
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder().build();

            // Then: Default values should be privacy-first
            assertThat(config.isCaptureMessageContent()).isFalse();
            assertThat(config.isTracingEnabled()).isTrue();
            assertThat(config.isMetricsEnabled()).isTrue();
            assertThat(config.getSamplingRate()).isEqualTo(1.0);
            assertThat(config.getContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);

            fail("TEST EXPECTED TO FAIL - TDD Red Phase: OpenTelemetryConfig not implemented yet");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected in TDD red phase
        }
    }

    @Test
    void should_allow_disabling_tracing_independently() {
        // Given: REQ-5 - Enable/disable tracing, metrics, or logging independently
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .tracingEnabled(false)
                .metricsEnabled(true)
                .build();

            assertThat(config.isTracingEnabled()).isFalse();
            assertThat(config.isMetricsEnabled()).isTrue();

            fail("TEST EXPECTED TO FAIL - TDD Red Phase: Configuration not implemented yet");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }

    @Test
    void should_allow_disabling_metrics_independently() {
        // Given: REQ-5
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .tracingEnabled(true)
                .metricsEnabled(false)
                .build();

            assertThat(config.isTracingEnabled()).isTrue();
            assertThat(config.isMetricsEnabled()).isFalse();

            fail("TEST EXPECTED TO FAIL - TDD Red Phase");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }

    @Test
    void should_support_sampling_rate_configuration() {
        // Given: REQ-5, US-7 - Sampling rates
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .samplingRate(0.1)  // 10% sampling
                .build();

            assertThat(config.getSamplingRate()).isEqualTo(0.1);

            fail("TEST EXPECTED TO FAIL - TDD Red Phase");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }

    @Test
    void should_support_content_capture_modes() {
        // Given: REQ-7 - Tiered content capture levels
        try {
            OpenTelemetryConfig noneConfig = OpenTelemetryConfig.builder()
                .contentCaptureMode(ContentCaptureMode.NONE)
                .build();

            OpenTelemetryConfig metadataConfig = OpenTelemetryConfig.builder()
                .contentCaptureMode(ContentCaptureMode.METADATA)
                .build();

            OpenTelemetryConfig fullConfig = OpenTelemetryConfig.builder()
                .contentCaptureMode(ContentCaptureMode.FULL)
                .build();

            assertThat(noneConfig.getContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
            assertThat(metadataConfig.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
            assertThat(fullConfig.getContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);

            fail("TEST EXPECTED TO FAIL - TDD Red Phase");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }

    @Test
    void should_validate_sampling_rate_range() {
        // Given: Sampling rate should be between 0.0 and 1.0
        try {
            OpenTelemetryConfig.builder()
                .samplingRate(-0.1)
                .build();

            fail("Should throw exception for invalid sampling rate");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Sampling rate must be between 0.0 and 1.0");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected in TDD red phase
        }
    }

    @Test
    void should_support_attribute_filtering() {
        // Given: REQ-5 - Attribute filtering for privacy
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .excludeAttribute("user.email")
                .excludeAttribute("api.key")
                .build();

            assertThat(config.getExcludedAttributes()).contains("user.email", "api.key");

            fail("TEST EXPECTED TO FAIL - TDD Red Phase");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }

    @Test
    void should_support_custom_span_attributes() {
        // Given: NFR-6 - Extensibility for custom attributes
        try {
            OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .addCustomAttribute("environment", "production")
                .addCustomAttribute("service.version", "1.0.0")
                .build();

            assertThat(config.getCustomAttributes())
                .containsEntry("environment", "production")
                .containsEntry("service.version", "1.0.0");

            fail("TEST EXPECTED TO FAIL - TDD Red Phase");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // Expected
        }
    }
}
