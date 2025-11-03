package dev.langchain4j.opentelemetry;

import dev.langchain4j.observability.api.event.*;
import dev.langchain4j.invocation.InvocationContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * TDD Red Phase: Tests for OpenTelemetry AiServiceListener integration.
 *
 * This test class validates REQ-1 (AI Service tracing), REQ-1 (Tool execution), and REQ-1 (Guardrails).
 *
 * Expected behavior: All tests should FAIL until OpenTelemetryAiServiceListener is implemented.
 */
class OpenTelemetryAiServiceListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryAiServiceStartedListener startedListener;
    private OpenTelemetryAiServiceCompletedListener completedListener;
    private OpenTelemetryToolExecutedListener toolListener;

    @BeforeEach
    void setUp() {
        try {
            startedListener = new OpenTelemetryAiServiceStartedListener(
                otelTesting.getOpenTelemetry().getTracer("test-tracer")
            );
            completedListener = new OpenTelemetryAiServiceCompletedListener();
            toolListener = new OpenTelemetryToolExecutedListener(
                otelTesting.getOpenTelemetry().getTracer("test-tracer")
            );
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            fail("OpenTelemetry AiService listeners not found - TDD red phase as expected");
        }
    }

    @Test
    void should_create_ai_service_span_with_invocation_context() {
        // Given: REQ-1 - AI Service method invocation tracing
        InvocationContext invocationContext = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("dev.langchain4j.Assistant")
            .methodName("chat")
            .methodArguments(List.of("What is AI?"))
            .timestamp(Instant.now())
            .build();

        AiServiceStartedEvent startedEvent = new AiServiceStartedEvent(invocationContext);

        // When
        startedListener.onEvent(startedEvent);

        // Simulate completion
        AiServiceCompletedEvent completedEvent = new AiServiceCompletedEvent(
            invocationContext, "AI is artificial intelligence..."
        );
        completedListener.onEvent(completedEvent);

        // Then: Span should capture AI service invocation
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("Assistant.chat");
        assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify invocation metadata
        assertThat(span.getAttributes().get("ai.service.interface")).isEqualTo("dev.langchain4j.Assistant");
        assertThat(span.getAttributes().get("ai.service.method")).isEqualTo("chat");

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: AiService listeners not implemented yet");
    }

    @Test
    void should_create_child_span_for_tool_execution() {
        // Given: REQ-1 - Tool/function execution spans (US-6)
        InvocationContext invocationContext = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("dev.langchain4j.Assistant")
            .methodName("calculateWithTools")
            .methodArguments(List.of("Calculate 2+2"))
            .timestamp(Instant.now())
            .build();

        AiServiceStartedEvent startedEvent = new AiServiceStartedEvent(invocationContext);
        startedListener.onEvent(startedEvent);

        // When: Tool is executed
        ToolExecutedEvent toolEvent = ToolExecutedEvent.builder()
            .invocationContext(invocationContext)
            .toolName("calculator")
            .toolArguments("{\"a\": 2, \"b\": 2}")
            .toolResult("4")
            .executionTimeMs(50L)
            .build();

        toolListener.onEvent(toolEvent);

        // Then: Tool execution should create child span
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData toolSpan = spans.stream()
            .filter(s -> s.getName().equals("tool.execution.calculator"))
            .findFirst()
            .orElseThrow();

        assertThat(toolSpan.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(toolSpan.getAttributes().get("tool.name")).isEqualTo("calculator");
        // Tool arguments should be sanitized by default
        assertThat(toolSpan.getAttributes().get("tool.arguments")).isNotNull();

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Tool execution tracing not implemented yet");
    }

    @Test
    void should_record_guardrail_execution_as_span_event() {
        // Given: REQ-1 - Guardrail validation tracking
        InvocationContext invocationContext = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("dev.langchain4j.Assistant")
            .methodName("chatWithGuardrails")
            .methodArguments(List.of("User message"))
            .timestamp(Instant.now())
            .build();

        AiServiceStartedEvent startedEvent = new AiServiceStartedEvent(invocationContext);
        startedListener.onEvent(startedEvent);

        // When: Input guardrail executes
        InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
            .invocationContext(invocationContext)
            .guardrailName("ContentFilter")
            .result("PASS")
            .executionTimeMs(10L)
            .build();

        OpenTelemetryInputGuardrailListener guardrailListener =
            new OpenTelemetryInputGuardrailListener();
        guardrailListener.onEvent(guardrailEvent);

        // Then: Guardrail should be recorded as span event
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().stream()
            .anyMatch(e -> e.getName().equals("guardrail.input.ContentFilter")))
            .isTrue();

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Guardrail tracing not implemented yet");
    }

    @Test
    void should_maintain_hierarchical_span_structure() {
        // Given: REQ-2 (Span Hierarchy Design) - Hierarchical spans
        InvocationContext invocationContext = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("dev.langchain4j.Assistant")
            .methodName("complexOperation")
            .methodArguments(List.of("Complex task"))
            .timestamp(Instant.now())
            .build();

        // When: AI Service -> Tool -> Nested operation
        startedListener.onEvent(new AiServiceStartedEvent(invocationContext));

        toolListener.onEvent(ToolExecutedEvent.builder()
            .invocationContext(invocationContext)
            .toolName("searchDatabase")
            .toolArguments("{\"query\": \"test\"}")
            .toolResult("results")
            .executionTimeMs(100L)
            .build());

        completedListener.onEvent(new AiServiceCompletedEvent(invocationContext, "Done"));

        // Then: Span hierarchy should reflect call structure
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData parentSpan = spans.stream()
            .filter(s -> s.getName().equals("Assistant.complexOperation"))
            .findFirst()
            .orElseThrow();

        SpanData toolSpan = spans.stream()
            .filter(s -> s.getName().startsWith("tool.execution"))
            .findFirst()
            .orElseThrow();

        assertThat(toolSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
        assertThat(toolSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Span hierarchy not implemented yet");
    }

    @Test
    void should_handle_ai_service_errors() {
        // Given: REQ-8 - Error tracking
        InvocationContext invocationContext = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("dev.langchain4j.Assistant")
            .methodName("chat")
            .methodArguments(List.of("Test"))
            .timestamp(Instant.now())
            .build();

        startedListener.onEvent(new AiServiceStartedEvent(invocationContext));

        // When: Error occurs
        OpenTelemetryAiServiceErrorListener errorListener =
            new OpenTelemetryAiServiceErrorListener();

        RuntimeException error = new RuntimeException("Service unavailable");
        AiServiceErrorEvent errorEvent = new AiServiceErrorEvent(invocationContext, error);
        errorListener.onEvent(errorEvent);

        // Then: Span should record error
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Service unavailable");

        fail("TEST EXPECTED TO FAIL - TDD Red Phase: Error handling not implemented yet");
    }
}
