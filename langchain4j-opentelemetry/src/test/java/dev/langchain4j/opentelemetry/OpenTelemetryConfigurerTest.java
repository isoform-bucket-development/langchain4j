package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryConfigurerTest {

    @Test
    void should_configure_opentelemetry() {
        // When
        OpenTelemetry openTelemetry = OpenTelemetryConfigurer.configure();

        // Then
        assertThat(openTelemetry).isNotNull();
    }

    @Test
    void should_get_global_opentelemetry() {
        // When
        OpenTelemetry openTelemetry = OpenTelemetryConfigurer.getGlobalOpenTelemetry();

        // Then
        assertThat(openTelemetry).isNotNull();
    }
}
