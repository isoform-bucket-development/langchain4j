package dev.langchain4j.opentelemetry;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
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

class OpenTelemetryToolExecutionListenerTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetry openTelemetry;
    private OpenTelemetryToolExecutionListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        listener = new OpenTelemetryToolExecutionListener(openTelemetry);
    }

    @Test
    void should_create_internal_span_for_tool_execution() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("calculator")
                .arguments("{\"a\": 5, \"b\": 3}")
                .build();

        ToolExecutedEvent event = new ToolExecutedEvent(
                context,
                toolRequest,
                "8",
                null
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("tool.calculator");
        assertThat(span.getKind().name()).isEqualTo("INTERNAL");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("tool.name")))
                .isEqualTo("calculator");
    }

    @Test
    void should_not_capture_parameters_by_default() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("get_user_info")
                .arguments("{\"ssn\": \"123-45-6789\"}")
                .build();

        ToolExecutedEvent event = new ToolExecutedEvent(
                context,
                toolRequest,
                "User info",
                null
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        // Parameters should NOT be captured by default
        assertThat(span.getAttributes().asMap()).doesNotContainKey(
                io.opentelemetry.api.common.AttributeKey.stringKey("tool.arguments"));
        assertThat(span.getAttributes().asMap()).doesNotContainKey(
                io.opentelemetry.api.common.AttributeKey.stringKey("tool.result"));
    }

    @Test
    void should_capture_parameters_when_enabled() {
        // Given
        OpenTelemetryToolExecutionListener listenerWithParams =
                new OpenTelemetryToolExecutionListener(openTelemetry, true);

        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("calculator")
                .arguments("{\"a\": 5, \"b\": 3}")
                .build();

        ToolExecutedEvent event = new ToolExecutedEvent(
                context,
                toolRequest,
                "8",
                null
        );

        // When
        listenerWithParams.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("tool.arguments")))
                .isEqualTo("{\"a\": 5, \"b\": 3}");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("tool.result")))
                .isEqualTo("8");
    }

    @Test
    void should_record_tool_execution_error() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        InvocationContext context = InvocationContext.builder()
                .invocationId("test-invocation")
                .attributes(attributes)
                .build();

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("failing_tool")
                .arguments("{}")
                .build();

        RuntimeException error = new RuntimeException("Tool execution failed");
        ToolExecutedEvent event = new ToolExecutedEvent(
                context,
                toolRequest,
                null,
                error
        );

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Tool execution failed");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")))
                .isEqualTo("RuntimeException");
    }
}
