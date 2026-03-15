package dev.langchain4j.opentelemetry.config;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.metrics.GenAiMetrics;
import dev.langchain4j.opentelemetry.metrics.TokenUsageRecorder;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Map;

/**
 * A ChatModelListener that respects configuration settings for enabling/disabling
 * tracing and metrics independently.
 * <p>
 * This listener integrates both span tracing and metrics recording, allowing each
 * to be independently controlled via {@link OpenTelemetryLangChain4jConfig}.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
 *     .tracingEnabled(true)
 *     .metricsEnabled(false)
 *     .build();
 *
 * ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
 *     .config(config)
 *     .tracerProvider(tracerProvider)
 *     .meterProvider(meterProvider)
 *     .build();
 * }</pre>
 */
public class ConfiguredChatModelListener implements ChatModelListener {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    // Keys for storing span data in the attributes map
    private static final String SPAN_KEY = "otel.span";
    private static final String SCOPE_KEY = "otel.scope";
    private static final String CONTEXT_KEY = "otel.context";
    private static final String START_TIME_KEY = "otel.start_time";
    private static final String PROVIDER_KEY = "otel.provider";
    private static final String MODEL_KEY = "otel.model";

    private final OpenTelemetryLangChain4jConfig config;
    private final Tracer tracer;
    private final TokenUsageRecorder tokenUsageRecorder;

    private ConfiguredChatModelListener(Builder builder) {
        this.config = builder.config != null ? builder.config : OpenTelemetryLangChain4jConfig.defaultConfig();
        this.tracer = builder.tracerProvider.get(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.tokenUsageRecorder = TokenUsageRecorder.builder()
                .meterProvider(builder.meterProvider)
                .build();
    }

    /**
     * Creates a new builder for ConfiguredChatModelListener.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest request = requestContext.chatRequest();
        ModelProvider provider = requestContext.modelProvider();
        Map<Object, Object> attributes = requestContext.attributes();

        // Store provider and model for metrics recording in onResponse
        if (provider != null) {
            attributes.put(PROVIDER_KEY, provider.name().toLowerCase());
        }
        String modelName = request.modelName();
        if (modelName != null) {
            attributes.put(MODEL_KEY, modelName);
        }
        attributes.put(START_TIME_KEY, System.currentTimeMillis());

        // Only create span if tracing is enabled
        if (!config.isTracingEnabled()) {
            return;
        }

        // Build span attributes from request
        AttributesBuilder attrBuilder = Attributes.builder();

        if (provider != null) {
            attrBuilder.put(GenAiAttributes.GEN_AI_SYSTEM, provider.name().toLowerCase());
        }

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
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        ChatResponse response = responseContext.chatResponse();

        // Record metrics if enabled
        if (config.isMetricsEnabled()) {
            recordMetrics(response, attributes);
        }

        // Handle span if tracing was enabled
        if (!config.isTracingEnabled()) {
            // Clean up stored attributes
            cleanupAttributes(attributes);
            return;
        }

        Span span = (Span) attributes.get(SPAN_KEY);
        Scope scope = (Scope) attributes.get(SCOPE_KEY);

        if (span == null) {
            return;
        }

        try {
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
            cleanupAttributes(attributes);
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Map<Object, Object> attributes = errorContext.attributes();

        // Handle span if tracing was enabled
        if (!config.isTracingEnabled()) {
            cleanupAttributes(attributes);
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
            cleanupAttributes(attributes);
        }
    }

    private void recordMetrics(ChatResponse response, Map<Object, Object> attributes) {
        TokenUsage tokenUsage = response.tokenUsage();
        if (tokenUsage == null) {
            return;
        }

        Integer inputTokens = tokenUsage.inputTokenCount();
        Integer outputTokens = tokenUsage.outputTokenCount();

        if (inputTokens == null && outputTokens == null) {
            return;
        }

        String provider = (String) attributes.get(PROVIDER_KEY);
        String model = (String) attributes.get(MODEL_KEY);

        tokenUsageRecorder.recordTokenUsage(
                inputTokens != null ? inputTokens.longValue() : 0,
                outputTokens != null ? outputTokens.longValue() : 0,
                provider,
                model,
                "chat"
        );
    }

    private void cleanupAttributes(Map<Object, Object> attributes) {
        attributes.remove(SPAN_KEY);
        attributes.remove(SCOPE_KEY);
        attributes.remove(CONTEXT_KEY);
        attributes.remove(START_TIME_KEY);
        attributes.remove(PROVIDER_KEY);
        attributes.remove(MODEL_KEY);
    }

    /**
     * Returns the configuration used by this listener.
     *
     * @return the configuration
     */
    public OpenTelemetryLangChain4jConfig getConfig() {
        return config;
    }

    /**
     * Returns whether tracing is enabled.
     *
     * @return true if tracing is enabled
     */
    public boolean isTracingEnabled() {
        return config.isTracingEnabled();
    }

    /**
     * Returns whether metrics are enabled.
     *
     * @return true if metrics are enabled
     */
    public boolean isMetricsEnabled() {
        return config.isMetricsEnabled();
    }

    /**
     * Builder for creating ConfiguredChatModelListener instances.
     */
    public static class Builder {
        private OpenTelemetryLangChain4jConfig config;
        private TracerProvider tracerProvider = GlobalOpenTelemetry.getTracerProvider();
        private MeterProvider meterProvider = GlobalOpenTelemetry.getMeterProvider();

        private Builder() {}

        /**
         * Sets the configuration.
         *
         * @param config the configuration
         * @return this builder
         */
        public Builder config(OpenTelemetryLangChain4jConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Sets the tracer provider.
         *
         * @param tracerProvider the tracer provider
         * @return this builder
         */
        public Builder tracerProvider(TracerProvider tracerProvider) {
            this.tracerProvider = tracerProvider;
            return this;
        }

        /**
         * Sets the meter provider.
         *
         * @param meterProvider the meter provider
         * @return this builder
         */
        public Builder meterProvider(MeterProvider meterProvider) {
            this.meterProvider = meterProvider;
            return this;
        }

        /**
         * Builds the ConfiguredChatModelListener.
         *
         * @return the configured listener
         */
        public ConfiguredChatModelListener build() {
            return new ConfiguredChatModelListener(this);
        }
    }
}
