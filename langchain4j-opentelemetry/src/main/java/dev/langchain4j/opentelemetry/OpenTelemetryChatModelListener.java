package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry instrumentation for LangChain4j ChatModel operations.
 *
 * This listener implements:
 * - REQ-1: Automatic Trace Generation for chat model requests
 * - REQ-2: Semantic Convention Compliance (OpenTelemetry GenAI conventions)
 * - REQ-3: Metrics Collection (token usage, duration, error rates)
 * - REQ-4: Context Propagation (W3C Trace Context)
 * - REQ-7: Content Capture Options (privacy-first)
 * - REQ-8: Error and Exception Tracking
 *
 * Thread-safe for concurrent LLM requests (NFR-3).
 */
public class OpenTelemetryChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryChatModelListener.class);

    private static final String SPAN_NAME = "gen_ai.chat";
    private static final String START_TIME_KEY = "otel.start.time.nanos";
    private static final String SPAN_KEY = "otel.span";

    private final Tracer tracer;
    private final Meter meter;
    private final OpenTelemetryConfig config;

    // Metrics instruments
    private final LongCounter tokenUsageCounter;
    private final DoubleHistogram operationDurationHistogram;
    private final LongCounter operationErrorCounter;

    // Thread-safe storage for spans across request/response lifecycle
    private final Map<ChatModelRequestContext, Span> activeSpans = new ConcurrentHashMap<>();

    public OpenTelemetryChatModelListener(Tracer tracer, Meter meter, OpenTelemetryConfig config) {
        this.tracer = tracer;
        this.meter = meter;
        this.config = config;

        // Initialize metrics instruments (REQ-3)
        this.tokenUsageCounter = meter
            .counterBuilder("gen_ai.client.token.usage")
            .setDescription("Token usage for LLM operations")
            .setUnit("tokens")
            .build();

        this.operationDurationHistogram = meter
            .histogramBuilder("gen_ai.client.operation.duration")
            .setDescription("Duration of LLM operations")
            .setUnit("s")  // seconds
            .build();

        this.operationErrorCounter = meter
            .counterBuilder("gen_ai.client.operation.error")
            .setDescription("Count of LLM operation errors")
            .build();
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        if (!config.isTracingEnabled()) {
            return;
        }

        try {
            ChatRequest request = requestContext.chatRequest();

            // REQ-4: Context propagation - automatically inherits parent context
            Span span = tracer.spanBuilder(SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current())  // Automatic parent context detection
                .startSpan();

            // Store span for access in onResponse/onError
            activeSpans.put(requestContext, span);
            requestContext.attributes().put(SPAN_KEY, span);

            // Store start time for duration metrics
            requestContext.attributes().put(START_TIME_KEY, System.nanoTime());

            // REQ-2: Semantic convention attributes
            addSemanticAttributes(span, request);

            // REQ-7: Content capture (opt-in only)
            if (config.getContentCaptureMode() != ContentCaptureMode.NONE) {
                addMessageContentEvents(span, request);
            }

            // Add custom attributes (NFR-6)
            for (Map.Entry<String, String> entry : config.getCustomAttributes().entrySet()) {
                span.setAttribute(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            log.error("Error in OpenTelemetry onRequest", e);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        if (!config.isTracingEnabled()) {
            return;
        }

        ChatModelRequestContext requestCtx = responseContext.chatModelRequestContext();
        Span span = activeSpans.remove(requestCtx);
        if (span == null) {
            span = (Span) responseContext.attributes().get(SPAN_KEY);
        }

        if (span == null) {
            log.warn("No active span found for response context");
            return;
        }

        try {
            ChatResponse response = responseContext.chatResponse();
            ChatRequest request = requestCtx.chatRequest();

            // Add response attributes
            addResponseAttributes(span, response);

            // REQ-7: Capture response content if enabled
            if (config.isCaptureMessageContent() || config.getContentCaptureMode() == ContentCaptureMode.FULL) {
                addResponseContentEvents(span, response);
            }

            // REQ-3: Record metrics
            if (config.isMetricsEnabled()) {
                recordMetrics(request, response, requestCtx);
            }

            // Complete span with OK status
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            log.error("Error in OpenTelemetry onResponse", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Error processing response: " + e.getMessage());
        } finally {
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        if (!config.isTracingEnabled()) {
            return;
        }

        ChatModelRequestContext requestCtx = errorContext.chatModelRequestContext();
        Span span = activeSpans.remove(requestCtx);
        if (span == null) {
            span = (Span) errorContext.attributes().get(SPAN_KEY);
        }

        if (span == null) {
            log.warn("No active span found for error context");
            return;
        }

        try {
            Throwable error = errorContext.error();

            // REQ-8: Error and exception tracking
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());

            // Record error metric
            if (config.isMetricsEnabled()) {
                recordErrorMetric(requestCtx.chatRequest());
            }

        } catch (Exception e) {
            log.error("Error in OpenTelemetry onError", e);
        } finally {
            span.end();
        }
    }

    /**
     * Add semantic convention attributes according to OpenTelemetry GenAI spec (REQ-2).
     */
    private void addSemanticAttributes(Span span, ChatRequest request) {
        String modelName = request.modelName();

        // Infer provider from model name
        String provider = inferProvider(modelName);
        if (provider != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_SYSTEM, provider);
        }

        if (modelName != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_MODEL, modelName);
        }

        if (request.temperature() != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE, request.temperature());
        }

        Integer maxOutputTokens = request.parameters().maxOutputTokens();
        if (maxOutputTokens != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS, maxOutputTokens);
        }

        if (request.topP() != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_REQUEST_TOP_P, request.topP());
        }
    }

    /**
     * Add response attributes to span.
     */
    private void addResponseAttributes(Span span, ChatResponse response) {
        // Token usage
        TokenUsage tokenUsage = response.tokenUsage();
        if (tokenUsage != null) {
            if (tokenUsage.inputTokenCount() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, tokenUsage.inputTokenCount().longValue());
            }
            if (tokenUsage.outputTokenCount() != null) {
                span.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, tokenUsage.outputTokenCount().longValue());
            }
        }

        // Finish reason
        FinishReason finishReason = response.finishReason();
        if (finishReason != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS,
                List.of(finishReason.toString().toLowerCase()));
        }

        // Response ID
        if (response.id() != null) {
            span.setAttribute(GenAiAttributes.GEN_AI_RESPONSE_ID, response.id());
        }
    }

    /**
     * Add message content as span events (opt-in via config).
     */
    private void addMessageContentEvents(Span span, ChatRequest request) {
        if (request.messages() == null) {
            return;
        }

        for (ChatMessage message : request.messages()) {
            if (config.getContentCaptureMode() == ContentCaptureMode.FULL) {
                // FULL mode: capture complete message content
                String content = extractMessageText(message);
                if (content != null) {
                    span.addEvent("gen_ai.prompt",
                        Attributes.of(
                            AttributeKey.stringKey("message.role"), message.type().toString(),
                            AttributeKey.stringKey("message.content"), content
                        ));
                }
            } else if (config.getContentCaptureMode() == ContentCaptureMode.METADATA) {
                // METADATA mode: capture only role, no content
                span.addEvent("gen_ai.prompt.metadata",
                    Attributes.of(AttributeKey.stringKey("message.role"), message.type().toString()));
            }
        }
    }

    /**
     * Extract text content from a ChatMessage, handling different message types.
     */
    private String extractMessageText(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return ((dev.langchain4j.data.message.UserMessage) message).singleText();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            return ((dev.langchain4j.data.message.SystemMessage) message).text();
        } else if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
            return ((dev.langchain4j.data.message.ToolExecutionResultMessage) message).text();
        }
        return null;
    }

    /**
     * Add response content as span events (opt-in via config).
     */
    private void addResponseContentEvents(Span span, ChatResponse response) {
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage != null && aiMessage.text() != null) {
            span.addEvent("gen_ai.completion",
                Attributes.of(AttributeKey.stringKey("message.content"), aiMessage.text()));
        }
    }

    /**
     * Record OpenTelemetry metrics for token usage, duration, etc. (REQ-3).
     */
    private void recordMetrics(ChatRequest request, ChatResponse response, ChatModelRequestContext requestContext) {
        String modelName = request.modelName();
        String provider = inferProvider(modelName);

        // Build common attributes for all metrics
        AttributesBuilder attributesBuilder = Attributes.builder()
            .put("gen_ai.request.model", modelName != null ? modelName : "unknown")
            .put("gen_ai.system", provider != null ? provider : "unknown")
            .put("operation_type", "chat");

        Attributes commonAttributes = attributesBuilder.build();

        // Record token usage
        TokenUsage tokenUsage = response.tokenUsage();
        if (tokenUsage != null) {
            if (tokenUsage.inputTokenCount() != null) {
                Attributes inputTokenAttrs = attributesBuilder
                    .put("token.type", "input")
                    .build();
                tokenUsageCounter.add(tokenUsage.inputTokenCount().longValue(), inputTokenAttrs);
            }

            if (tokenUsage.outputTokenCount() != null) {
                Attributes outputTokenAttrs = attributesBuilder
                    .put("token.type", "output")
                    .build();
                tokenUsageCounter.add(tokenUsage.outputTokenCount().longValue(), outputTokenAttrs);
            }
        }

        // Record operation duration
        Long startTimeNanos = (Long) requestContext.attributes().get(START_TIME_KEY);
        if (startTimeNanos != null) {
            long durationNanos = System.nanoTime() - startTimeNanos;
            double durationSeconds = durationNanos / 1_000_000_000.0;
            operationDurationHistogram.record(durationSeconds, commonAttributes);
        }
    }

    /**
     * Record error metric when operation fails.
     */
    private void recordErrorMetric(ChatRequest request) {
        String modelName = request.modelName();
        String provider = inferProvider(modelName);

        Attributes errorAttributes = Attributes.builder()
            .put("gen_ai.request.model", modelName != null ? modelName : "unknown")
            .put("gen_ai.system", provider != null ? provider : "unknown")
            .build();

        operationErrorCounter.add(1, errorAttributes);
    }

    /**
     * Infer LLM provider from model name (REQ-2).
     *
     * Patterns:
     * - gpt-* → openai
     * - claude-* → anthropic
     * - gemini-* → google
     * - mistral-* → mistralai
     * - llama-* → meta
     * - command-* → cohere
     */
    private String inferProvider(String modelName) {
        if (modelName == null) {
            return "unknown";
        }

        String lowerModel = modelName.toLowerCase();

        if (lowerModel.startsWith("gpt-") || lowerModel.startsWith("text-davinci") || lowerModel.startsWith("o1-")) {
            return "openai";
        } else if (lowerModel.startsWith("claude-")) {
            return "anthropic";
        } else if (lowerModel.startsWith("gemini-")) {
            return "google";
        } else if (lowerModel.startsWith("mistral-")) {
            return "mistralai";
        } else if (lowerModel.startsWith("llama-")) {
            return "meta";
        } else if (lowerModel.startsWith("command-")) {
            return "cohere";
        } else if (lowerModel.contains("ollama")) {
            return "ollama";
        }

        return "unknown";
    }
}
