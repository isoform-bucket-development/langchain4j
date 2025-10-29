package dev.langchain4j.opentelemetry;

import dev.langchain4j.observability.api.event.*;
import dev.langchain4j.observability.api.listener.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry listener for AI Service invocations.
 * Creates root INTERNAL spans for each AI Service invocation with proper lifecycle management.
 * <p>
 * Span naming convention: AiService.{interfaceName}.{methodName}
 * <p>
 * Captured attributes:
 * - invocation.id: Unique invocation identifier
 * - ai_service.interface: AI Service interface name
 * - ai_service.method: Method name
 * - chat_memory_id: Chat memory identifier (if available)
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryAiServiceListener implements
        AiServiceStartedListener,
        AiServiceResponseReceivedListener,
        AiServiceCompletedListener,
        AiServiceErrorListener {

    private static final String OTEL_SPAN_KEY = "otel.span";
    private static final String OTEL_CONTEXT_KEY = "otel.context";
    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.8.0";

    private final Tracer tracer;
    private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();

    public OpenTelemetryAiServiceListener(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    @Override
    public void onEvent(AiServiceStartedEvent event) {
        String invocationId = event.invocationContext().invocationId();
        String interfaceName = event.invocationContext().interfaceName();
        String methodName = event.invocationContext().methodName();

        // Create root INTERNAL span for AI Service invocation
        String spanName = String.format("AiService.%s.%s",
                interfaceName != null ? interfaceName : "Unknown",
                methodName != null ? methodName : "unknown");

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        // Set attributes
        if (invocationId != null) {
            span.setAttribute("invocation.id", invocationId);
        }
        if (interfaceName != null) {
            span.setAttribute("ai_service.interface", interfaceName);
        }
        if (methodName != null) {
            span.setAttribute("ai_service.method", methodName);
        }
        if (event.invocationContext().chatMemoryId() != null) {
            span.setAttribute("chat_memory_id", event.invocationContext().chatMemoryId());
        }

        // Store span for later retrieval
        activeSpans.put(invocationId, span);

        // Store span and context in invocation attributes for child spans
        Context context = Context.current().with(span);
        event.invocationContext().attributes().put(OTEL_SPAN_KEY, span);
        event.invocationContext().attributes().put(OTEL_CONTEXT_KEY, context);
    }

    @Override
    public void onEvent(AiServiceResponseReceivedEvent event) {
        // Optional: Add event to span for response received
        String invocationId = event.invocationContext().invocationId();
        Span span = activeSpans.get(invocationId);
        if (span != null) {
            span.addEvent("response_received");
        }
    }

    @Override
    public void onEvent(AiServiceCompletedEvent event) {
        String invocationId = event.invocationContext().invocationId();
        Span span = activeSpans.remove(invocationId);
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.end();
        }
    }

    @Override
    public void onEvent(AiServiceErrorEvent event) {
        String invocationId = event.invocationContext().invocationId();
        Span span = activeSpans.remove(invocationId);
        if (span != null) {
            span.setStatus(StatusCode.ERROR, event.error().getMessage());
            span.recordException(event.error());
            span.end();
        }
    }
}
