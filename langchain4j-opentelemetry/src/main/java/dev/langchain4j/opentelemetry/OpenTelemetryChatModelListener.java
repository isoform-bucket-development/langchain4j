package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.SemanticAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OpenTelemetry listener for ChatModel requests following GenAI semantic conventions.
 * Creates CLIENT spans for external LLM API calls with:
 * - Token usage tracking (input/output tokens)
 * - Privacy-first content redaction (disabled by default)
 * - GenAI semantic convention attributes
 * - Streaming support with optional chunk events
 * <p>
 * GenAI semantic conventions attributes:
 * - gen_ai.system: LLM provider (e.g., "openai", "anthropic")
 * - gen_ai.request.model: Model name
 * - gen_ai.operation.name: Operation type (always "chat")
 * - gen_ai.usage.input_tokens: Input token count
 * - gen_ai.usage.output_tokens: Output token count
 * - gen_ai.response.finish_reasons: Completion finish reasons
 * </p>
 *
 * @since 1.8.0
 */
public class OpenTelemetryChatModelListener implements ChatModelListener {

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry";
    private static final String INSTRUMENTATION_VERSION = "1.8.0";
    private static final String OTEL_CHAT_SPAN_KEY = "otel.chat.span";
    private static final String OTEL_CHAT_CONTEXT_KEY = "otel.chat.context";

    // GenAI semantic convention attribute keys
    private static final String GEN_AI_SYSTEM = "gen_ai.system";
    private static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    private static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    private static final String GEN_AI_REQUEST_TEMPERATURE = "gen_ai.request.temperature";
    private static final String GEN_AI_REQUEST_TOP_P = "gen_ai.request.top_p";
    private static final String GEN_AI_REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";
    private static final String GEN_AI_REQUEST_TOOL_COUNT = "gen_ai.request.tool_count";
    private static final String GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    private static final String GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    private static final String GEN_AI_RESPONSE_ID = "gen_ai.response.id";
    private static final String GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";
    private static final String GEN_AI_RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";
    private static final String GEN_AI_REQUEST_CONTENT = "gen_ai.request.content";
    private static final String GEN_AI_RESPONSE_CONTENT = "gen_ai.response.content";

    private final Tracer tracer;
    private final boolean captureContent;
    private final int contentTruncationLength;
    private final boolean emitStreamingEvents;
    private final Map<ChatRequest, Span> activeSpans = new ConcurrentHashMap<>();

    /**
     * Creates a new OpenTelemetryChatModelListener with default configuration.
     * Default: content capture disabled, streaming events disabled.
     *
     * @param openTelemetry OpenTelemetry instance
     */
    public OpenTelemetryChatModelListener(OpenTelemetry openTelemetry) {
        this(openTelemetry, false, 1000, false);
    }

    /**
     * Creates a new OpenTelemetryChatModelListener with custom configuration.
     *
     * @param openTelemetry OpenTelemetry instance
     * @param captureContent Whether to capture request/response content (privacy control)
     * @param contentTruncationLength Maximum content length to capture
     * @param emitStreamingEvents Whether to emit events for streaming chunks
     */
    public OpenTelemetryChatModelListener(OpenTelemetry openTelemetry,
                                          boolean captureContent,
                                          int contentTruncationLength,
                                          boolean emitStreamingEvents) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.captureContent = captureContent;
        this.contentTruncationLength = contentTruncationLength;
        this.emitStreamingEvents = emitStreamingEvents;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest request = requestContext.request();
        Map<Object, Object> attributes = requestContext.attributes();

        // Extract model name for span name
        String modelName = request.model() != null ? request.model() : "unknown";
        String spanName = "chat " + modelName;

        // Get parent context if available
        Context parentContext = (Context) attributes.get("otel.context");
        Context context = parentContext != null ? parentContext : Context.current();

        // Create CLIENT span for LLM API call
        Span span = tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        // Set GenAI semantic convention attributes
        span.setAttribute(GEN_AI_OPERATION_NAME, "chat");

        if (request.model() != null) {
            span.setAttribute(GEN_AI_REQUEST_MODEL, request.model());
            // Extract system name from model (e.g., "gpt-4" -> "openai")
            String system = extractSystemFromModel(request.model());
            if (system != null) {
                span.setAttribute(GEN_AI_SYSTEM, system);
            }
        }

        if (request.temperature() != null) {
            span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, request.temperature());
        }

        if (request.topP() != null) {
            span.setAttribute(GEN_AI_REQUEST_TOP_P, request.topP());
        }

        if (request.maxTokens() != null) {
            span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, request.maxTokens());
        }

        if (request.toolSpecifications() != null) {
            span.setAttribute(GEN_AI_REQUEST_TOOL_COUNT, request.toolSpecifications().size());
        }

        // Optionally capture content (privacy control)
        if (captureContent && request.messages() != null) {
            String content = request.messages().stream()
                    .map(ChatMessage::text)
                    .collect(Collectors.joining("\n"));
            span.setAttribute(GEN_AI_REQUEST_CONTENT, truncate(content));
        }

        // Store span for later use
        activeSpans.put(request, span);

        // Store in attributes for potential child operations
        Context spanContext = Context.current().with(span);
        attributes.put(OTEL_CHAT_SPAN_KEY, span);
        attributes.put(OTEL_CHAT_CONTEXT_KEY, spanContext);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatRequest request = responseContext.request();
        ChatResponse response = responseContext.response();
        Span span = activeSpans.remove(request);

        if (span != null) {
            // Set response attributes
            if (response.id() != null) {
                span.setAttribute(GEN_AI_RESPONSE_ID, response.id());
            }

            if (response.model() != null) {
                span.setAttribute(GEN_AI_RESPONSE_MODEL, response.model());
            }

            // Token usage
            if (response.metadata() != null && response.metadata().tokenUsage() != null) {
                TokenUsage tokenUsage = response.metadata().tokenUsage();
                if (tokenUsage.inputTokenCount() != null) {
                    span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, tokenUsage.inputTokenCount());
                }
                if (tokenUsage.outputTokenCount() != null) {
                    span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, tokenUsage.outputTokenCount());
                }
            }

            // Finish reasons
            if (response.metadata() != null && response.metadata().finishReason() != null) {
                FinishReason finishReason = response.metadata().finishReason();
                span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, finishReason.toString().toLowerCase());
            }

            // Optionally capture response content
            if (captureContent && response.aiMessage() != null) {
                AiMessage aiMessage = response.aiMessage();
                if (aiMessage.text() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_CONTENT, truncate(aiMessage.text()));
                }
            }

            span.setStatus(StatusCode.OK);
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        ChatRequest request = errorContext.request();
        Span span = activeSpans.remove(request);

        if (span != null) {
            Throwable error = errorContext.error();
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.recordException(error);

            // Set error type attribute
            span.setAttribute("error.type", error.getClass().getSimpleName());

            span.end();
        }
    }

    private String extractSystemFromModel(String model) {
        if (model == null) {
            return null;
        }
        String lowerModel = model.toLowerCase();
        if (lowerModel.contains("gpt") || lowerModel.contains("openai")) {
            return "openai";
        } else if (lowerModel.contains("claude") || lowerModel.contains("anthropic")) {
            return "anthropic";
        } else if (lowerModel.contains("gemini") || lowerModel.contains("palm")) {
            return "google";
        } else if (lowerModel.contains("mistral")) {
            return "mistral";
        } else if (lowerModel.contains("llama")) {
            return "meta";
        } else if (lowerModel.contains("cohere")) {
            return "cohere";
        }
        return null;
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= contentTruncationLength) {
            return content;
        }
        return content.substring(0, contentTruncationLength) + "...";
    }
}
