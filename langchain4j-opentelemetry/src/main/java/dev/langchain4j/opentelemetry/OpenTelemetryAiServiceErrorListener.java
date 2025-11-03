package dev.langchain4j.opentelemetry;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.listener.AiServiceErrorEventListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry listener for AI Service error events.
 *
 * Implements REQ-8: Error and Exception Tracking for AI Service operations.
 * Records exception details and completes the span with ERROR status.
 */
public class OpenTelemetryAiServiceErrorListener implements AiServiceErrorEventListener {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryAiServiceErrorListener.class);

    @Override
    public void onEvent(AiServiceErrorEvent event) {
        try {
            InvocationContext invocationContext = event.invocationContext();
            Throwable error = event.error();

            // Retrieve the span started by the Started listener
            Span span = OpenTelemetryAiServiceStartedListener.removeSpan(invocationContext.invocationId());

            if (span != null) {
                // REQ-8: Record exception and set error status
                span.recordException(error);
                span.setStatus(StatusCode.ERROR, error.getMessage());
                span.end();
            } else {
                log.debug("No active span found for failed AI Service invocation: {}",
                    invocationContext.invocationId());
            }

        } catch (Exception e) {
            log.error("Error in OpenTelemetry AI Service error listener", e);
        }
    }
}
