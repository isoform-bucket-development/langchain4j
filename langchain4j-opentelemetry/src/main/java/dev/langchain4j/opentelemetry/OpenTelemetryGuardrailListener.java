package dev.langchain4j.opentelemetry;

import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * OpenTelemetry listener for guardrail executions (input and output).
 * Creates INTERNAL child spans for guardrail validations with result tracking.
 * <p>
 * Span naming conventions:
 * - Input guardrails: guardrail.input.{guardrailClassName}
 * - Output guardrails: guardrail.output.{guardrailClassName}
 * <p>
 * Captured attributes:
 * - guardrail.type: "input" or "output"
 * - guardrail.name: Guardrail class name
 * - guardrail.result: "success", "blocked", or "reprompt"
 * - guardrail.reason: Failure/blocking reason (if available)
 * - guardrail.reprompt_count: Reprompt attempt count (for output guardrails)
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryGuardrailListener implements
        InputGuardrailExecutedListener,
        OutputGuardrailExecutedListener {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.8.0";

    private final Tracer tracer;

    public OpenTelemetryGuardrailListener(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    @Override
    public void onEvent(InputGuardrailExecutedEvent event) {
        String guardrailName = event.guardrail() != null
                ? event.guardrail().getClass().getSimpleName()
                : "unknown";

        Context parentContext = (Context) event.invocationContext().attributes().get("otel.context");
        Context context = parentContext != null ? parentContext : Context.current();

        String spanName = "guardrail.input." + guardrailName;
        Span span = tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        span.setAttribute("guardrail.type", "input");
        span.setAttribute("guardrail.name", guardrailName);

        recordGuardrailResult(span, event.result());

        span.end();
    }

    @Override
    public void onEvent(OutputGuardrailExecutedEvent event) {
        String guardrailName = event.guardrail() != null
                ? event.guardrail().getClass().getSimpleName()
                : "unknown";

        Context parentContext = (Context) event.invocationContext().attributes().get("otel.context");
        Context context = parentContext != null ? parentContext : Context.current();

        String spanName = "guardrail.output." + guardrailName;
        Span span = tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        span.setAttribute("guardrail.type", "output");
        span.setAttribute("guardrail.name", guardrailName);

        recordGuardrailResult(span, event.result());

        span.end();
    }

    private void recordGuardrailResult(Span span, GuardrailResult result) {
        if (result == null) {
            span.setAttribute("guardrail.result", "unknown");
            span.setStatus(StatusCode.ERROR, "No guardrail result");
            return;
        }

        if (result.isSuccess()) {
            span.setAttribute("guardrail.result", "success");
            span.setStatus(StatusCode.OK);
        } else if (result.validationFailure() != null) {
            span.setAttribute("guardrail.result", "blocked");
            span.setAttribute("guardrail.reason",
                    result.validationFailure().toString());
            span.setStatus(StatusCode.ERROR, "Guardrail blocked");
        } else if (result.reprompt() != null) {
            span.setAttribute("guardrail.result", "reprompt");
            // Reprompt is not an error - it's working as intended
            span.setStatus(StatusCode.OK);
        } else {
            span.setAttribute("guardrail.result", "unknown");
            span.setStatus(StatusCode.UNSET);
        }
    }
}
