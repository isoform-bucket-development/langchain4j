package dev.langchain4j.opentelemetry;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedEventListener;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry listener for input guardrail execution events.
 *
 * This listener implements:
 * - REQ-1: Guardrail validation tracking
 *
 * Records guardrail execution as span events on the parent AI Service span.
 */
public class OpenTelemetryInputGuardrailListener implements InputGuardrailExecutedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInputGuardrailListener.class);

    private static final AttributeKey<String> GUARDRAIL_NAME = AttributeKey.stringKey("guardrail.name");
    private static final AttributeKey<String> GUARDRAIL_RESULT = AttributeKey.stringKey("guardrail.result");
    private static final AttributeKey<Long> GUARDRAIL_EXECUTION_TIME_MS = AttributeKey.longKey("guardrail.execution_time_ms");

    @Override
    public void onEvent(InputGuardrailExecutedEvent event) {
        try {
            InvocationContext invocationContext = event.invocationContext();
            String guardrailName = event.guardrailName();

            // Get parent AI Service span to add event
            Span parentSpan = OpenTelemetryAiServiceStartedListener.getSpan(invocationContext.invocationId());

            if (parentSpan != null) {
                // Add guardrail execution as span event
                String eventName = "guardrail.input." + guardrailName;

                Attributes.Builder attributesBuilder = Attributes.builder()
                    .put(GUARDRAIL_NAME, guardrailName);

                String result = event.result();
                if (result != null) {
                    attributesBuilder.put(GUARDRAIL_RESULT, result);
                }

                Long executionTimeMs = event.executionTimeMs();
                if (executionTimeMs != null) {
                    attributesBuilder.put(GUARDRAIL_EXECUTION_TIME_MS, executionTimeMs);
                }

                parentSpan.addEvent(eventName, attributesBuilder.build());
            } else {
                log.debug("No parent span found for guardrail event: {}", guardrailName);
            }

        } catch (Exception e) {
            log.error("Error in OpenTelemetry input guardrail listener", e);
        }
    }
}
