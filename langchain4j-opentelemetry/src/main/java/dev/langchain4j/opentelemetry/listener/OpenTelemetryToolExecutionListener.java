package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenTelemetry instrumentation for tool executions within AiService invocations.
 * Creates child spans for each tool execution with tool name, arguments, and results.
 *
 * <p>The span hierarchy follows the pattern:
 * <pre>
 * [AiService.methodName]      (created by AiServiceListener)
 *   └─ [gen_ai.chat]          (created by ChatModelListener)
 *       └─ [tool.execution.*] (created by this listener)
 * </pre>
 *
 * <p>This listener implements {@link ToolExecutedEventListener} to handle
 * tool execution events and create appropriate spans with:
 * <ul>
 *   <li>Tool name as span name suffix</li>
 *   <li>Tool arguments (with sensitive data sanitization)</li>
 *   <li>Tool execution result</li>
 *   <li>Error status and exception events for failed executions</li>
 * </ul>
 */
public final class OpenTelemetryToolExecutionListener implements ToolExecutedEventListener {

    private static final String INSTRUMENTATION_NAME = "langchain4j-opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    // Pattern to match sensitive field names in JSON arguments
    private static final Set<String> SENSITIVE_FIELD_PATTERNS = Set.of(
            "password", "secret", "key", "token", "credential", "auth",
            "api_key", "apikey", "api-key", "access_token", "accesstoken",
            "private_key", "privatekey", "private-key"
    );

    // Pattern to match JSON key-value pairs
    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[^,}\\]]+)"
    );

    private final Tracer tracer;
    private final OpenTelemetryLangChain4jConfig config;

    private OpenTelemetryToolExecutionListener(Builder builder) {
        this.tracer = builder.tracerProvider.get(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.config = builder.config;
    }

    /**
     * Creates a new builder for constructing OpenTelemetryToolExecutionListener instances.
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
    public static OpenTelemetryToolExecutionListener create() {
        return builder().build();
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        if (!config.isTracingEnabled()) {
            return;
        }

        ToolExecutionRequest request = event.request();
        String toolName = request.name();
        String spanName = GenAiSpanNames.toolExecution(toolName);

        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);

        // Set parent context from current context
        Context parentContext = Context.current();
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }

        Span span = spanBuilder.startSpan();

        try {
            // Set tool name attribute
            span.setAttribute(GenAiAttributes.TOOL_NAME, toolName);

            // Set tool execution ID if available
            if (request.id() != null) {
                span.setAttribute(GenAiAttributes.TOOL_EXECUTION_ID, request.id());
            }

            // Set invocation context attributes
            InvocationContext ctx = event.invocationContext();
            if (ctx != null) {
                span.setAttribute(GenAiAttributes.AISERVICE_INVOCATION_ID, ctx.invocationId().toString());
            }

            // Set operation name
            span.setAttribute(GenAiAttributes.GEN_AI_OPERATION_NAME, "tool_execution");

            // Capture arguments based on content capture mode
            if (config.getContentCaptureMode() == ContentCaptureMode.FULL) {
                String arguments = request.arguments();
                if (arguments != null) {
                    String sanitizedArguments = sanitizeArguments(arguments);
                    span.setAttribute(GenAiAttributes.TOOL_ARGUMENTS, sanitizedArguments);
                }

                // Capture result
                String result = event.resultText();
                if (result != null) {
                    span.setAttribute(GenAiAttributes.TOOL_RESULT, result);
                }
            }

            // Check if the result indicates an error (result could contain exception info)
            if (isErrorResult(event.resultText())) {
                span.setStatus(StatusCode.ERROR, "Tool execution failed");
                // Record the error details as an exception event
                span.recordException(new ToolExecutionException(event.resultText()));
            } else {
                span.setStatus(StatusCode.OK);
            }

        } finally {
            span.end();
        }
    }

    /**
     * Sanitizes tool arguments by redacting sensitive field values.
     * This method scans for field names that match common sensitive patterns
     * (password, secret, key, token, etc.) and replaces their values with "[REDACTED]".
     *
     * @param arguments the original JSON arguments string
     * @return sanitized arguments with sensitive values redacted
     */
    String sanitizeArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return arguments;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = JSON_FIELD_PATTERN.matcher(arguments);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this match
            result.append(arguments, lastEnd, matcher.start());

            String fieldName = matcher.group(1).toLowerCase();
            String fieldValue = matcher.group(2);

            // Check if this field name matches any sensitive patterns
            boolean isSensitive = SENSITIVE_FIELD_PATTERNS.stream()
                    .anyMatch(fieldName::contains);

            if (isSensitive) {
                // Redact the sensitive value
                result.append("\"").append(matcher.group(1)).append("\": \"[REDACTED]\"");
            } else {
                // Keep the original field and value
                result.append(matcher.group());
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        result.append(arguments.substring(lastEnd));

        return result.toString();
    }

    /**
     * Checks if the result text indicates an error condition.
     * This is a simple heuristic that looks for common error indicators.
     *
     * @param resultText the tool execution result
     * @return true if the result appears to indicate an error
     */
    private boolean isErrorResult(String resultText) {
        if (resultText == null) {
            return false;
        }
        String lowerResult = resultText.toLowerCase();
        return lowerResult.startsWith("error:") ||
                lowerResult.startsWith("exception:") ||
                lowerResult.contains("java.lang.") && lowerResult.contains("exception");
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
     * Exception wrapper for tool execution errors to record in spans.
     */
    private static class ToolExecutionException extends RuntimeException {
        ToolExecutionException(String message) {
            super(message);
        }
    }

    /**
     * Builder for {@link OpenTelemetryToolExecutionListener}.
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
         * @return a new OpenTelemetryToolExecutionListener
         */
        public OpenTelemetryToolExecutionListener build() {
            if (tracerProvider == null) {
                tracerProvider = GlobalOpenTelemetry.getTracerProvider();
            }
            return new OpenTelemetryToolExecutionListener(this);
        }
    }
}
