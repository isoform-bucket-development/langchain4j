package dev.langchain4j.opentelemetry;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * OpenTelemetry listener for tool executions.
 * Creates INTERNAL child spans for tool invocations with privacy-respecting parameter handling.
 * <p>
 * Span naming convention: tool.{toolName}
 * <p>
 * Captured attributes:
 * - tool.name: Tool name
 * - tool.arguments: Tool arguments (optional, disabled by default for privacy)
 * - tool.result: Tool result (optional, disabled by default for privacy)
 * - error.type: Error type if tool execution fails
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryToolExecutionListener implements ToolExecutedEventListener {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.8.0";

    private final Tracer tracer;
    private final boolean captureParameters;

    /**
     * Creates a new OpenTelemetryToolExecutionListener with default configuration.
     * Default: parameter capture disabled (privacy-first).
     *
     * @param openTelemetry OpenTelemetry instance
     */
    public OpenTelemetryToolExecutionListener(OpenTelemetry openTelemetry) {
        this(openTelemetry, false);
    }

    /**
     * Creates a new OpenTelemetryToolExecutionListener with custom configuration.
     *
     * @param openTelemetry OpenTelemetry instance
     * @param captureParameters Whether to capture tool parameters and results
     */
    public OpenTelemetryToolExecutionListener(OpenTelemetry openTelemetry, boolean captureParameters) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.captureParameters = captureParameters;
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        String toolName = event.toolExecutionRequest() != null
                ? event.toolExecutionRequest().name()
                : "unknown";

        // Get parent context from invocation attributes
        Context parentContext = (Context) event.invocationContext().attributes().get("otel.context");
        Context context = parentContext != null ? parentContext : Context.current();

        // Create INTERNAL span for tool execution
        String spanName = "tool." + toolName;
        Span span = tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        // Set tool name attribute
        span.setAttribute("tool.name", toolName);

        // Optionally capture tool arguments (privacy control)
        if (captureParameters && event.toolExecutionRequest() != null) {
            String arguments = event.toolExecutionRequest().arguments();
            if (arguments != null) {
                span.setAttribute("tool.arguments", arguments);
            }
        }

        // Optionally capture tool result
        if (captureParameters && event.result() != null) {
            span.setAttribute("tool.result", event.result());
        }

        // Check for errors
        if (event.error() != null) {
            span.setStatus(StatusCode.ERROR, event.error().getMessage());
            span.recordException(event.error());
            span.setAttribute("error.type", event.error().getClass().getSimpleName());
        } else {
            span.setStatus(StatusCode.OK);
        }

        span.end();
    }
}
