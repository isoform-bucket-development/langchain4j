package dev.langchain4j.opentelemetry;

import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.ValidationFailure;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.internal.InvocationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenTelemetryGuardrailListenerTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetry openTelemetry;
    private OpenTelemetryGuardrailListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        listener = new OpenTelemetryGuardrailListener(openTelemetry);
    }

    @Test
    void should_create_span_for_successful_input_guardrail() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        Guardrail guardrail = mock(Guardrail.class);
        GuardrailResult result = GuardrailResult.success();

        InputGuardrailExecutedEvent event = new InputGuardrailExecutedEvent(
                context,
                guardrail,
                result
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).contains("guardrail.input");
        assertThat(span.getKind().name()).isEqualTo("INTERNAL");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.type")))
                .isEqualTo("input");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.result")))
                .isEqualTo("success");
    }

    @Test
    void should_record_blocked_input_guardrail() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        Guardrail guardrail = mock(Guardrail.class);
        ValidationFailure failure = new ValidationFailure("Content contains profanity");
        GuardrailResult result = GuardrailResult.failure(failure);

        InputGuardrailExecutedEvent event = new InputGuardrailExecutedEvent(
                context,
                guardrail,
                result
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.result")))
                .isEqualTo("blocked");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.reason")))
                .isNotNull();
    }

    @Test
    void should_create_span_for_successful_output_guardrail() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        Guardrail guardrail = mock(Guardrail.class);
        GuardrailResult result = GuardrailResult.success();

        OutputGuardrailExecutedEvent event = new OutputGuardrailExecutedEvent(
                context,
                guardrail,
                result
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).contains("guardrail.output");
        assertThat(span.getKind().name()).isEqualTo("INTERNAL");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.type")))
                .isEqualTo("output");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.result")))
                .isEqualTo("success");
    }

    @Test
    void should_record_reprompt_output_guardrail() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        Guardrail guardrail = mock(Guardrail.class);
        GuardrailResult result = GuardrailResult.reprompt("Improve response quality");

        OutputGuardrailExecutedEvent event = new OutputGuardrailExecutedEvent(
                context,
                guardrail,
                result
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.result")))
                .isEqualTo("reprompt");
    }

    @Test
    void should_record_failed_output_guardrail() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        Guardrail guardrail = mock(Guardrail.class);
        ValidationFailure failure = new ValidationFailure("Output validation failed");
        GuardrailResult result = GuardrailResult.failure(failure);

        OutputGuardrailExecutedEvent event = new OutputGuardrailExecutedEvent(
                context,
                guardrail,
                result
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("guardrail.result")))
                .isEqualTo("blocked");
    }
}
