package dev.langchain4j.opentelemetry;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedEventListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry listener for AI Service method completion events.
 *
 * Completes the span started by OpenTelemetryAiServiceStartedListener.
 */
public class OpenTelemetryAiServiceCompletedListener implements AiServiceCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryAiServiceCompletedListener.class);

    @Override
    public void onEvent(AiServiceCompletedEvent event) {
        try {
            InvocationContext invocationContext = event.invocationContext();

            // Retrieve the span started by the Started listener
            Span span = OpenTelemetryAiServiceStartedListener.removeSpan(invocationContext.invocationId());

            if (span != null) {
                // Complete span with OK status
                span.setStatus(StatusCode.OK);
                span.end();
            } else {
                log.debug("No active span found for completed AI Service invocation: {}",
                    invocationContext.invocationId());
            }

        } catch (Exception e) {
            log.error("Error in OpenTelemetry AI Service completed listener", e);
        }
    }
}
