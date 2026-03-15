package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.internal.SpanContextManager;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenTelemetry instrumentation for AiService method invocations.
 * Creates hierarchical spans for AiService method calls, enabling
 * correlation with nested ChatModel spans.
 *
 * <p>This class provides separate listener implementations for different
 * AiService events:
 * <ul>
 *   <li>{@link AiServiceStartedListener} - Creates a parent span when an AiService method is invoked</li>
 *   <li>{@link AiServiceCompletedListener} - Ends the span when the method completes successfully</li>
 *   <li>{@link AiServiceErrorListener} - Ends the span with error status when the method fails</li>
 *   <li>{@link InputGuardrailExecutedListener} - Creates child spans for input guardrail executions</li>
 *   <li>{@link OutputGuardrailExecutedListener} - Creates child spans for output guardrail executions</li>
 * </ul>
 *
 * <p>The span hierarchy follows the pattern:
 * <pre>
 * [AiService.methodName]      (created by this listener)
 *   ├─ [guardrail.input.*]    (created by this listener)
 *   ├─ [gen_ai.chat]          (created by ChatModelListener)
 *   │   └─ [tool.execution.*] (created by ToolExecutedEventListener)
 *   └─ [guardrail.output.*]   (created by this listener)
 * </pre>
 */
public final class OpenTelemetryAiServiceListener {

    private static final String INSTRUMENTATION_NAME = "langchain4j-opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private final SpanContextManager spanContextManager;
    private final OpenTelemetryLangChain4jConfig config;
    private final Tracer tracer;

    private final AiServiceStartedListener startedListener;
    private final AiServiceCompletedListener completedListener;
    private final AiServiceErrorListener errorListener;
    private final InputGuardrailExecutedListener inputGuardrailListener;
    private final OutputGuardrailExecutedListener outputGuardrailListener;

    private OpenTelemetryAiServiceListener(Builder builder) {
        this.tracer = builder.tracerProvider.get(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.spanContextManager = new SpanContextManager(tracer);
        this.config = builder.config;

        this.startedListener = this::onStarted;
        this.completedListener = this::onCompleted;
        this.errorListener = this::onError;
        this.inputGuardrailListener = this::onInputGuardrailExecuted;
        this.outputGuardrailListener = this::onOutputGuardrailExecuted;
    }

    /**
     * Creates a new builder for constructing OpenTelemetryAiServiceListener instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a listener with default configuration using GlobalOpenTelemetry.
     *
     * @return a new listener with default settings
     */
    public static OpenTelemetryAiServiceListener create() {
        return builder().build();
    }

    private void onStarted(AiServiceStartedEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        InvocationContext ctx = event.invocationContext();
        String spanName = GenAiSpanNames.aiServiceMethod(ctx.methodName());

        Span span = spanContextManager.startSpan(spanName, ctx.invocationId());

        // Add standard attributes
        span.setAttribute(GenAiAttributes.AISERVICE_INTERFACE_NAME, ctx.interfaceName());
        span.setAttribute(GenAiAttributes.AISERVICE_METHOD_NAME, ctx.methodName());
        span.setAttribute(GenAiAttributes.AISERVICE_INVOCATION_ID, ctx.invocationId().toString());
        span.setAttribute(GenAiAttributes.GEN_AI_OPERATION_NAME, "aiservice");

        // Capture content based on configuration
        if (config.getContentCaptureMode() == ContentCaptureMode.FULL) {
            // Capture system message if present
            event.systemMessage().ifPresent(systemMessage -> {
                span.setAttribute(GenAiAttributes.GEN_AI_SYSTEM_MESSAGE, systemMessage.text());
            });

            // Capture user message
            if (event.userMessage() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_USER_MESSAGE, event.userMessage().singleText());
            }
        }
    }

    private void onCompleted(AiServiceCompletedEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        InvocationContext ctx = event.invocationContext();
        Span span = spanContextManager.getSpan(ctx.invocationId());

        if (span != null) {
            // Capture result if content capture is enabled
            if (config.getContentCaptureMode() == ContentCaptureMode.FULL) {
                event.result().ifPresent(result -> {
                    String resultStr = result.toString();
                    span.setAttribute(GenAiAttributes.GEN_AI_COMPLETION, resultStr);
                });
            }

            spanContextManager.endSpan(ctx.invocationId(), StatusCode.OK);
        }
    }

    private void onError(AiServiceErrorEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        InvocationContext ctx = event.invocationContext();
        Throwable error = event.error();

        spanContextManager.recordException(ctx.invocationId(), error);
        spanContextManager.endSpan(ctx.invocationId(), StatusCode.ERROR, error.getMessage());
    }

    private void onInputGuardrailExecuted(InputGuardrailExecutedEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        InvocationContext ctx = event.invocationContext();
        String guardrailName = getGuardrailSimpleName(event.guardrailClass());
        String spanName = GenAiSpanNames.guardrailInput(guardrailName);

        // Get the parent context from the active AiService span
        Context parentContext = spanContextManager.getContext(ctx.invocationId());

        // Create child span for guardrail execution
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }

        Span guardrailSpan = spanBuilder.startSpan();
        try (Scope ignored = guardrailSpan.makeCurrent()) {
            // Set guardrail attributes
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_NAME, guardrailName);
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_TYPE, "input");
            guardrailSpan.setAttribute(GenAiAttributes.AISERVICE_INVOCATION_ID, ctx.invocationId().toString());

            GuardrailResult<?> result = event.result();
            boolean passed = result.isSuccess();

            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_PASS, passed);
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_RESULT, result.result().name());

            if (passed) {
                guardrailSpan.setStatus(StatusCode.OK);
            } else {
                guardrailSpan.setStatus(StatusCode.ERROR);

                // Capture failure details
                List<?> failures = result.failures();
                if (!failures.isEmpty()) {
                    String failureMessage = failures.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("; "));
                    guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_FAILURE_MESSAGE, failureMessage);
                }
            }
        } finally {
            guardrailSpan.end();
        }
    }

    private void onOutputGuardrailExecuted(OutputGuardrailExecutedEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        InvocationContext ctx = event.invocationContext();
        String guardrailName = getGuardrailSimpleName(event.guardrailClass());
        String spanName = GenAiSpanNames.guardrailOutput(guardrailName);

        // Get the parent context from the active AiService span
        Context parentContext = spanContextManager.getContext(ctx.invocationId());

        // Create child span for guardrail execution
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }

        Span guardrailSpan = spanBuilder.startSpan();
        try (Scope ignored = guardrailSpan.makeCurrent()) {
            // Set guardrail attributes
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_NAME, guardrailName);
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_TYPE, "output");
            guardrailSpan.setAttribute(GenAiAttributes.AISERVICE_INVOCATION_ID, ctx.invocationId().toString());

            OutputGuardrailResult result = event.result();
            boolean passed = result.isSuccess();

            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_PASS, passed);
            guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_RESULT, result.result().name());

            if (passed) {
                guardrailSpan.setStatus(StatusCode.OK);
            } else {
                guardrailSpan.setStatus(StatusCode.ERROR);

                // Capture failure details
                List<?> failures = result.failures();
                if (!failures.isEmpty()) {
                    String failureMessage = failures.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("; "));
                    guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_FAILURE_MESSAGE, failureMessage);
                }

                // Check for retry/reprompt
                guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_RETRY, result.isRetry());
                guardrailSpan.setAttribute(GenAiAttributes.GUARDRAIL_REPROMPT, result.isReprompt());
            }
        } finally {
            guardrailSpan.end();
        }
    }

    /**
     * Extracts the simple class name from a guardrail class.
     *
     * @param guardrailClass the guardrail class
     * @return the simple class name
     */
    private String getGuardrailSimpleName(Class<?> guardrailClass) {
        if (guardrailClass == null) {
            return "unknown";
        }
        return guardrailClass.getSimpleName();
    }

    /**
     * Returns the listener for AiService started events.
     *
     * @return the started listener
     */
    public AiServiceStartedListener getStartedListener() {
        return startedListener;
    }

    /**
     * Returns the listener for AiService completed events.
     *
     * @return the completed listener
     */
    public AiServiceCompletedListener getCompletedListener() {
        return completedListener;
    }

    /**
     * Returns the listener for AiService error events.
     *
     * @return the error listener
     */
    public AiServiceErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * Returns the listener for input guardrail executed events.
     *
     * @return the input guardrail listener
     */
    public InputGuardrailExecutedListener getInputGuardrailListener() {
        return inputGuardrailListener;
    }

    /**
     * Returns the listener for output guardrail executed events.
     *
     * @return the output guardrail listener
     */
    public OutputGuardrailExecutedListener getOutputGuardrailListener() {
        return outputGuardrailListener;
    }

    /**
     * Returns all listeners as a list for convenient registration.
     *
     * @return list of all AI service listeners
     */
    public List<AiServiceListener<?>> getAllListeners() {
        return List.of(startedListener, completedListener, errorListener,
                inputGuardrailListener, outputGuardrailListener);
    }

    /**
     * Returns the SpanContextManager for use by other listeners that need
     * to create child spans (e.g., ChatModelListener for gen_ai.chat spans).
     *
     * @return the span context manager
     */
    public SpanContextManager getSpanContextManager() {
        return spanContextManager;
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public OpenTelemetryLangChain4jConfig getConfig() {
        return config;
    }

    /**
     * Builder for {@link OpenTelemetryAiServiceListener}.
     */
    public static final class Builder {

        private TracerProvider tracerProvider;
        private OpenTelemetryLangChain4jConfig config;

        private Builder() {
            this.config = OpenTelemetryLangChain4jConfig.defaultConfig();
        }

        /**
         * Sets the TracerProvider to use.
         *
         * @param tracerProvider the tracer provider
         * @return this builder
         */
        public Builder tracerProvider(TracerProvider tracerProvider) {
            this.tracerProvider = tracerProvider;
            return this;
        }

        /**
         * Sets the OpenTelemetry instance to use.
         *
         * @param openTelemetry the OpenTelemetry instance
         * @return this builder
         */
        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.tracerProvider = openTelemetry.getTracerProvider();
            return this;
        }

        /**
         * Sets the configuration.
         *
         * @param config the configuration
         * @return this builder
         */
        public Builder config(OpenTelemetryLangChain4jConfig config) {
            this.config = config != null ? config : OpenTelemetryLangChain4jConfig.defaultConfig();
            return this;
        }

        /**
         * Sets the content capture mode.
         *
         * @param mode the content capture mode
         * @return this builder
         */
        public Builder contentCaptureMode(ContentCaptureMode mode) {
            this.config = OpenTelemetryLangChain4jConfig.builder()
                    .tracingEnabled(config.isTracingEnabled())
                    .metricsEnabled(config.isMetricsEnabled())
                    .contentCaptureMode(mode)
                    .samplingRate(config.getSamplingRate())
                    .build();
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return a new OpenTelemetryAiServiceListener
         */
        public OpenTelemetryAiServiceListener build() {
            if (tracerProvider == null) {
                tracerProvider = GlobalOpenTelemetry.getTracerProvider();
            }
            return new OpenTelemetryAiServiceListener(this);
        }
    }
}
