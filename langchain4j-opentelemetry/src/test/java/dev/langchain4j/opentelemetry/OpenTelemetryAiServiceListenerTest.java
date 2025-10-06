package dev.langchain4j.opentelemetry;

import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for OpenTelemetryAiServiceListener.
 * These tests are designed to fail initially (TDD red phase) and validate:
 * 1. AiService invocations create root spans
 * 2. Proper span hierarchy and relationships
 * 3. GenAI semantic conventions are applied
 */
class OpenTelemetryAiServiceListenerTest {

    private OpenTelemetry openTelemetry;
    private InMemorySpanExporter spanExporter;
    private OpenTelemetryAiServiceListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build())
            .build();

        // This will fail because OpenTelemetryAiServiceListener doesn't exist yet
        try {
            listener = new OpenTelemetryAiServiceListener(openTelemetry.getTracer("langchain4j"));
        } catch (Exception e) {
            fail("OpenTelemetryAiServiceListener class not yet implemented: " + e.getMessage());
        }
    }

    @Test
    void shouldCreateRootSpanOnAiServiceStarted() {
        // This test will fail because OpenTelemetryAiServiceListener doesn't exist
        String invocationId = UUID.randomUUID().toString();

        // Mock AiServiceStartedEvent - this will fail because the event handling isn't implemented
        AiServiceStartedEvent event = createMockStartedEvent(invocationId);

        listener.onEvent(event);

        assertEquals(1, spanExporter.getFinishedSpanItems().size(),
            "Should create one root span for AiService invocation");

        var span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals("aiservice.invoke", span.getName(),
            "Root span should have 'aiservice.invoke' operation name");
        assertEquals(SpanKind.SERVER, span.getKind(),
            "Root span should be SERVER span kind");
    }

    @Test
    void shouldSetProperSpanAttributesOnStarted() {
        // This test will fail because attribute setting logic doesn't exist
        String invocationId = UUID.randomUUID().toString();
        String methodName = "generateResponse";

        AiServiceStartedEvent event = createMockStartedEventWithDetails(invocationId, methodName);

        listener.onEvent(event);

        var span = spanExporter.getFinishedSpanItems().get(0);

        assertTrue(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langchain4j.invocation.id")).equals(invocationId),
            "Span should include invocation ID attribute");
        assertTrue(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langchain4j.method.name")).equals(methodName),
            "Span should include method name attribute");
    }

    @Test
    void shouldCloseSpanOnAiServiceCompleted() {
        // This test will fail because completion handling doesn't exist
        String invocationId = UUID.randomUUID().toString();

        AiServiceStartedEvent startedEvent = createMockStartedEvent(invocationId);
        listener.onEvent(startedEvent);

        AiServiceCompletedEvent completedEvent = createMockCompletedEvent(invocationId);
        listener.onEvent(completedEvent);

        assertEquals(1, spanExporter.getFinishedSpanItems().size(),
            "Span should be finished after completion event");

        var span = spanExporter.getFinishedSpanItems().get(0);
        assertNotNull(span.getEndEpochNanos(),
            "Span should have end time set");
    }

    @Test
    void shouldRecordErrorOnAiServiceError() {
        // This test will fail because error handling doesn't exist
        String invocationId = UUID.randomUUID().toString();
        Exception testError = new RuntimeException("Test AI service error");

        AiServiceStartedEvent startedEvent = createMockStartedEvent(invocationId);
        listener.onEvent(startedEvent);

        AiServiceErrorEvent errorEvent = createMockErrorEvent(invocationId, testError);
        listener.onEvent(errorEvent);

        var span = spanExporter.getFinishedSpanItems().get(0);

        assertTrue(span.getStatus().isError(),
            "Span status should indicate error");
        assertTrue(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")).contains("RuntimeException"),
            "Span should record error type attribute");
    }

    @Test
    void shouldPropagateContextBetweenSpans() {
        // This test will fail because context propagation logic doesn't exist
        String invocationId = UUID.randomUUID().toString();

        // Test that spans created within an AiService invocation are properly parented
        AiServiceStartedEvent event = createMockStartedEvent(invocationId);
        listener.onEvent(event);

        // Simulate child span creation (would happen in ChatModelListener)
        Span childSpan = openTelemetry.getTracer("langchain4j")
            .spanBuilder("chat_model.generate")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan();
        childSpan.end();

        AiServiceCompletedEvent completedEvent = createMockCompletedEvent(invocationId);
        listener.onEvent(completedEvent);

        assertEquals(2, spanExporter.getFinishedSpanItems().size(),
            "Should have root span and child span");

        var rootSpan = spanExporter.getFinishedSpanItems().get(0);
        var childSpanData = spanExporter.getFinishedSpanItems().get(1);

        assertEquals(rootSpan.getTraceId(), childSpanData.getTraceId(),
            "Child span should have same trace ID as root span");
        assertEquals(rootSpan.getSpanId(), childSpanData.getParentSpanId(),
            "Child span should be parented to root span");
    }

    // Mock event creation methods - these will fail because the actual event classes may have different constructors
    private AiServiceStartedEvent createMockStartedEvent(String invocationId) {
        // This will fail because we don't know the exact constructor signature yet
        try {
            return new AiServiceStartedEvent(invocationId, "TestAiService", "generateResponse");
        } catch (Exception e) {
            fail("Unable to create mock AiServiceStartedEvent - constructor signature unknown: " + e.getMessage());
            return null;
        }
    }

    private AiServiceStartedEvent createMockStartedEventWithDetails(String invocationId, String methodName) {
        try {
            return new AiServiceStartedEvent(invocationId, "TestAiService", methodName);
        } catch (Exception e) {
            fail("Unable to create mock AiServiceStartedEvent with details: " + e.getMessage());
            return null;
        }
    }

    private AiServiceCompletedEvent createMockCompletedEvent(String invocationId) {
        try {
            return new AiServiceCompletedEvent(invocationId, "TestAiService", "generateResponse", "Success");
        } catch (Exception e) {
            fail("Unable to create mock AiServiceCompletedEvent: " + e.getMessage());
            return null;
        }
    }

    private AiServiceErrorEvent createMockErrorEvent(String invocationId, Exception error) {
        try {
            return new AiServiceErrorEvent(invocationId, "TestAiService", "generateResponse", error);
        } catch (Exception e) {
            fail("Unable to create mock AiServiceErrorEvent: " + e.getMessage());
            return null;
        }
    }
}