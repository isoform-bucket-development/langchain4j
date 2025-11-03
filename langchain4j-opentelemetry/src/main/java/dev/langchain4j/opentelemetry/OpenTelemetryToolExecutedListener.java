package dev.langchain4j.opentelemetry;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry listener for tool execution events.
 *
 * This listener implements:
 * - REQ-1: Tool/function execution tracing (US-6)
 * - Hierarchical span structure (tool spans as children of AI Service spans)
 *
 * Creates child spans named "tool.execution.{toolName}" that capture:
 * - Tool name
 * - Sanitized arguments
 * - Execution result
 * - Execution duration
 */
public class OpenTelemetryToolExecutedListener implements ToolExecutedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryToolExecutedListener.class);

    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("tool.name");
    private static final AttributeKey<String> TOOL_ARGUMENTS = AttributeKey.stringKey("tool.arguments");
    private static final AttributeKey<String> TOOL_RESULT = AttributeKey.stringKey("tool.result");
    private static final AttributeKey<Long> TOOL_EXECUTION_TIME_MS = AttributeKey.longKey("tool.execution_time_ms");

    private final Tracer tracer;

    public OpenTelemetryToolExecutedListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        try {
            InvocationContext invocationContext = event.invocationContext();
            String toolName = event.toolName();

            // Get parent span (AI Service span) for proper hierarchy
            Span parentSpan = OpenTelemetryAiServiceStartedListener.getSpan(invocationContext.invocationId());

            Context parentContext;
            if (parentSpan != null) {
                parentContext = Context.current().with(parentSpan);
            } else {
                parentContext = Context.current();
            }

            // Create child span for tool execution
            String spanName = "tool.execution." + toolName;
            Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(parentContext)
                .startSpan();

            // Add tool attributes
            span.setAttribute(TOOL_NAME, toolName);

            // Sanitize and add tool arguments
            String arguments = event.toolArguments();
            if (arguments != null) {
                String sanitizedArguments = sanitizeToolArguments(arguments);
                span.setAttribute(TOOL_ARGUMENTS, sanitizedArguments);
            }

            // Add tool result (also sanitized)
            String result = event.toolResult();
            if (result != null) {
                String sanitizedResult = sanitizeToolResult(result);
                span.setAttribute(TOOL_RESULT, sanitizedResult);
            }

            // Add execution time
            Long executionTimeMs = event.executionTimeMs();
            if (executionTimeMs != null) {
                span.setAttribute(TOOL_EXECUTION_TIME_MS, executionTimeMs);
            }

            // Complete span
            span.setStatus(StatusCode.OK);
            span.end();

        } catch (Exception e) {
            log.error("Error in OpenTelemetry tool execution listener", e);
        }
    }

    /**
     * Sanitize tool arguments to remove potential sensitive data.
     * This implements NFR-5: Security and Privacy.
     *
     * Applies basic sanitization patterns:
     * - Redact API keys, tokens, passwords
     * - Truncate very long arguments
     */
    private String sanitizeToolArguments(String arguments) {
        if (arguments == null) {
            return null;
        }

        String sanitized = arguments;

        // Redact common sensitive patterns
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key|token|password|secret)[\"']?\\s*[:=]\\s*[\"']?[^\\s,}\"']+", "$1: [REDACTED]");

        // Truncate if too long (max 1000 chars)
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000) + "... (truncated)";
        }

        return sanitized;
    }

    /**
     * Sanitize tool result to remove potential sensitive data.
     */
    private String sanitizeToolResult(String result) {
        if (result == null) {
            return null;
        }

        // Truncate if too long (max 1000 chars)
        if (result.length() > 1000) {
            return result.substring(0, 1000) + "... (truncated)";
        }

        return result;
    }
}
