package dev.langchain4j.opentelemetry;

import dev.langchain4j.observability.api.event.*;
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

class OpenTelemetryAiServiceListenerTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetry openTelemetry;
    private OpenTelemetryAiServiceListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        listener = new OpenTelemetryAiServiceListener(openTelemetry);
    }

    @Test
    void should_create_root_span_on_ai_service_started() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation-id")
                .interfaceName("TestInterface")
                .methodName("testMethod")
                .attributes(attributes)
                .build();
        AiServiceStartedEvent event = new AiServiceStartedEvent(context);

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).isEmpty(); // Span not finished yet

        // Verify span is stored in context
        assertThat(attributes).containsKey("otel.span");
        assertThat(attributes).containsKey("otel.context");
    }

    @Test
    void should_end_span_with_ok_status_on_completed() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation-id")
                .interfaceName("TestInterface")
                .methodName("testMethod")
                .attributes(attributes)
                .build();
        AiServiceStartedEvent startEvent = new AiServiceStartedEvent(context);
        AiServiceCompletedEvent completedEvent = new AiServiceCompletedEvent(context);

        // When
        listener.onEvent(startEvent);
        listener.onEvent(completedEvent);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("AiService.TestInterface.testMethod");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("invocation.id")))
                .isEqualTo("test-invocation-id");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ai_service.interface")))
                .isEqualTo("TestInterface");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ai_service.method")))
                .isEqualTo("testMethod");
    }

    @Test
    void should_end_span_with_error_status_on_error() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation-id")
                .interfaceName("TestInterface")
                .methodName("testMethod")
                .attributes(attributes)
                .build();
        AiServiceStartedEvent startEvent = new AiServiceStartedEvent(context);
        RuntimeException error = new RuntimeException("Test error");
        AiServiceErrorEvent errorEvent = new AiServiceErrorEvent(context, error);

        // When
        listener.onEvent(startEvent);
        listener.onEvent(errorEvent);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Test error");
    }

    @Test
    void should_include_chat_memory_id_when_present() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation-id")
                .interfaceName("TestInterface")
                .methodName("testMethod")
                .chatMemoryId("memory-123")
                .attributes(attributes)
                .build();
        AiServiceStartedEvent startEvent = new AiServiceStartedEvent(context);
        AiServiceCompletedEvent completedEvent = new AiServiceCompletedEvent(context);

        // When
        listener.onEvent(startEvent);
        listener.onEvent(completedEvent);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("chat_memory_id")))
                .isEqualTo("memory-123");
    }
}
