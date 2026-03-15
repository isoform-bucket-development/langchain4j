package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatModelListener implementation that generates OpenTelemetry spans and metrics.
 * <p>
 * This listener creates spans for both synchronous and streaming chat model operations,
 * with proper context propagation across async callbacks.
 * </p>
 */
public class OpenTelemetryChatModelListener implements ChatModelListener {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    // Keys for storing span data in the attributes map
    private static final String SPAN_KEY = "otel.span";
    private static final String SCOPE_KEY = "otel.scope";
    private static final String CONTEXT_KEY = "otel.context";
    private static final String START_TIME_KEY = "otel.start_time";
    private static final String TRACING_SKIPPED_KEY = "otel.tracing_skipped";

    private final Tracer tracer;
    private final boolean streaming;
    private final AtomicReference<OpenTelemetryLangChain4jConfig> configRef;

    /**
     * Creates a new listener using the global OpenTelemetry tracer.
     */
    public OpenTelemetryChatModelListener() {
        this(GlobalOpenTelemetry.getTracerProvider(), false, OpenTelemetryLangChain4jConfig.defaultConfig());
    }

    /**
     * Creates a new listener with the specified tracer provider.
     *
     * @param tracerProvider the tracer provider to use
     * @param streaming whether this listener is used for streaming operations
     */
    public OpenTelemetryChatModelListener(TracerProvider tracerProvider, boolean streaming) {
        this(tracerProvider, streaming, OpenTelemetryLangChain4jConfig.defaultConfig());
    }

    /**
     * Creates a new listener with the specified tracer provider and configuration.
     *
     * @param tracerProvider the tracer provider to use
     * @param streaming whether this listener is used for streaming operations
     * @param config the configuration to use
     */
    public OpenTelemetryChatModelListener(TracerProvider tracerProvider, boolean streaming,
                                          OpenTelemetryLangChain4jConfig config) {
        this.tracer = tracerProvider.get(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.streaming = streaming;
        this.configRef = new AtomicReference<>(config != null ? config : OpenTelemetryLangChain4jConfig.defaultConfig());
    }

    /**
     * Creates a builder for configuring the listener.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // Check if tracing is enabled in the current configuration
        OpenTelemetryLangChain4jConfig currentConfig = configRef.get();
        if (!currentConfig.isTracingEnabled()) {
            // Mark that tracing was skipped for this request
            requestContext.attributes().put(TRACING_SKIPPED_KEY, Boolean.TRUE);
            return;
        }

        ChatRequest request = requestContext.chatRequest();
        ModelProvider provider = requestContext.modelProvider();
        Map<Object, Object> attributes = requestContext.attributes();

        // Build span attributes from request
        AttributesBuilder attrBuilder = Attributes.builder();

        if (provider != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_SYSTEM, provider.name().toLowerCase());
        }

        String modelName = request.modelName();
        if (modelName != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MODEL, modelName);
        }

        Double temperature = request.temperature();
        if (temperature != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE, temperature);
        }

        Double topP = request.topP();
        if (topP != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_TOP_P, topP);
        }

        Integer maxOutputTokens = request.maxOutputTokens();
        if (maxOutputTokens != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS, maxOutputTokens.longValue());
        }

        attrBuilder.put(GenAiAttributes.GEN_AI_OPERATION_NAME, "chat");
        attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_STREAMING, streaming);

        // Capture parent context for proper context propagation
        Context parentContext = Context.current();

        // Create and start the span
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(parentContext)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        // Make span current and store scope for later cleanup
        Scope scope = span.makeCurrent();

        // Store span, scope, and context in attributes for retrieval in onResponse/onError
        attributes.put(SPAN_KEY, span);
        attributes.put(SCOPE_KEY, scope);
        attributes.put(CONTEXT_KEY, parentContext);
        attributes.put(START_TIME_KEY, System.currentTimeMillis());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();

        // Check if tracing was skipped for this request
        Boolean tracingSkipped = (Boolean) attributes.get(TRACING_SKIPPED_KEY);
        if (Boolean.TRUE.equals(tracingSkipped)) {
            attributes.remove(TRACING_SKIPPED_KEY);
            return;
        }

        Span span = (Span) attributes.get(SPAN_KEY);
        Scope scope = (Scope) attributes.get(SCOPE_KEY);

        if (span == null) {
            return;
        }

        try {
            ChatResponse response = responseContext.chatResponse();

            // Add response attributes
            String responseId = response.id();
            if (responseId != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_ID, responseId);
            }

            FinishReason finishReason = response.finishReason();
            if (finishReason != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS, List.of(finishReason.name()));
            }

            // Add token usage
            TokenUsage tokenUsage = response.tokenUsage();
            if (tokenUsage != null) {
                Integer inputTokens = tokenUsage.inputTokenCount();
                if (inputTokens != null) {
                    span.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, inputTokens.longValue());
                }

                Integer outputTokens = tokenUsage.outputTokenCount();
                if (outputTokens != null) {
                    span.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens.longValue());
                }

                Integer totalTokens = tokenUsage.totalTokenCount();
                if (totalTokens != null) {
                    span.setAttribute(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS, totalTokens.longValue());
                }
            }

            span.setStatus(StatusCode.OK);
        } finally {
            // Clean up
            if (scope != null) {
                scope.close();
            }
            span.end();

            // Remove from attributes
            attributes.remove(SPAN_KEY);
            attributes.remove(SCOPE_KEY);
            attributes.remove(CONTEXT_KEY);
            attributes.remove(START_TIME_KEY);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Map<Object, Object> attributes = errorContext.attributes();

        // Check if tracing was skipped for this request
        Boolean tracingSkipped = (Boolean) attributes.get(TRACING_SKIPPED_KEY);
        if (Boolean.TRUE.equals(tracingSkipped)) {
            attributes.remove(TRACING_SKIPPED_KEY);
            return;
        }

        Span span = (Span) attributes.get(SPAN_KEY);
        Scope scope = (Scope) attributes.get(SCOPE_KEY);

        if (span == null) {
            return;
        }

        try {
            Throwable error = errorContext.error();

            // Record the exception
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.setAttribute(GenAiAttributes.ERROR_TYPE, error.getClass().getName());
        } finally {
            // Clean up
            if (scope != null) {
                scope.close();
            }
            span.end();

            // Remove from attributes
            attributes.remove(SPAN_KEY);
            attributes.remove(SCOPE_KEY);
            attributes.remove(CONTEXT_KEY);
            attributes.remove(START_TIME_KEY);
        }
    }

    /**
     * Returns the tracer used by this listener.
     *
     * @return the tracer
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Returns whether this listener is configured for streaming operations.
     *
     * @return true if streaming, false otherwise
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Returns the current configuration.
     *
     * @return the current configuration
     */
    public OpenTelemetryLangChain4jConfig getConfig() {
        return configRef.get();
    }

    /**
     * Updates the configuration at runtime.
     * This allows enabling or disabling tracing without restarting the application.
     *
     * @param config the new configuration to use
     */
    public void updateConfig(OpenTelemetryLangChain4jConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        configRef.set(config);
    }

    /**
     * Builder for creating OpenTelemetryChatModelListener instances.
     */
    public static class Builder {
        private TracerProvider tracerProvider = GlobalOpenTelemetry.getTracerProvider();
        private boolean streaming = false;
        private OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.defaultConfig();

        /**
         * Sets the tracer provider to use.
         *
         * @param tracerProvider the tracer provider
         * @return this builder
         */
        public Builder tracerProvider(TracerProvider tracerProvider) {
            this.tracerProvider = tracerProvider;
            return this;
        }

        /**
         * Sets whether this listener is for streaming operations.
         *
         * @param streaming true for streaming, false otherwise
         * @return this builder
         */
        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        /**
         * Sets the configuration for this listener.
         *
         * @param config the configuration to use
         * @return this builder
         */
        public Builder config(OpenTelemetryLangChain4jConfig config) {
            this.config = config != null ? config : OpenTelemetryLangChain4jConfig.defaultConfig();
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return the configured listener
         */
        public OpenTelemetryChatModelListener build() {
            return new OpenTelemetryChatModelListener(tracerProvider, streaming, config);
        }
    }
}
