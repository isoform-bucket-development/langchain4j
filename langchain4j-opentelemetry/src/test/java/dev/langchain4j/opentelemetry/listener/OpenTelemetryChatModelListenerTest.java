package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenTelemetryChatModelListener with synchronous ChatModel operations.
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>Span generation for synchronous chat operations with correct naming</li>
 *   <li>Request attribute capture (model, temperature)</li>
 *   <li>Response attribute capture (token usage, finish reasons)</li>
 *   <li>Span status handling for successful operations</li>
 * </ul>
 */
class OpenTelemetryChatModelListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener listener;

    @BeforeEach
    void setUp() {
        listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(false)
                .build();
    }

    /**
     * Test Case 1: ChatRequest with user message 'Hello, world!'
     * Expected: Span with name 'gen_ai.chat' and status OK
     */
    @Test
    void chatRequestWithUserMessage_generatesSpanWithCorrectNameAndStatus() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello, world!"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello! How can I help you?"))
                .id("response-001")
                .tokenUsage(new TokenUsage(10, 15))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo(GenAiSpanNames.CHAT);
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify span ended properly
        assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
    }

    /**
     * Test Case 2: ChatRequest with model 'gpt-4o-mini' and temperature 0.7
     * Expected: Span attributes include gen_ai.request.model='gpt-4o-mini', gen_ai.request.temperature=0.7
     */
    @Test
    void chatRequestWithModelAndTemperature_capturesRequestAttributes() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is AI?"))
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("AI stands for Artificial Intelligence..."))
                .id("response-002")
                .tokenUsage(new TokenUsage(8, 25))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify request attributes
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4o-mini");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE))
                .isEqualTo(0.7);

        // Verify system attribute
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM))
                .isEqualTo("open_ai");

        // Verify operation name
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME))
                .isEqualTo("chat");
    }

    /**
     * Test Case 3: ChatResponse with 100 input tokens and 50 output tokens
     * Expected: Span attributes include gen_ai.usage.input_tokens=100, gen_ai.usage.output_tokens=50
     */
    @Test
    void chatResponseWithTokenUsage_capturesTokenCountAttributes() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Write a short story."))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Specific token counts for this test case
        int inputTokens = 100;
        int outputTokens = 50;

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Once upon a time..."))
                .id("response-003")
                .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify token usage attributes
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS))
                .isEqualTo(100L);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS))
                .isEqualTo(50L);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo(150L);
    }

    /**
     * Test Case 4: ChatResponse with finish_reason='stop'
     * Expected: Span attribute gen_ai.response.finish_reasons=['stop']
     */
    @Test
    void chatResponseWithFinishReason_capturesFinishReasonAttribute() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Say hello"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello!"))
                .id("response-004")
                .tokenUsage(new TokenUsage(5, 3))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify finish reason attribute
        List<String> finishReasons = span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS);
        assertThat(finishReasons).containsExactly("STOP");

        // Verify response ID attribute
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_ID))
                .isEqualTo("response-004");
    }

    /**
     * Additional test: Verify top_p and max_tokens are captured when provided
     */
    @Test
    void chatRequestWithAllParameters_capturesAllRequestAttributes() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Generate creative content"))
                .modelName("gpt-4-turbo")
                .temperature(0.9)
                .topP(0.95)
                .maxOutputTokens(500)
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Creative content here..."))
                .id("response-005")
                .tokenUsage(new TokenUsage(20, 100))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify all request attributes
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4-turbo");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE))
                .isEqualTo(0.9);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TOP_P))
                .isEqualTo(0.95);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS))
                .isEqualTo(500L);

        // Verify streaming flag is false for sync operations
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING))
                .isFalse();
    }

    /**
     * Additional test: Verify error handling captures error status
     */
    @Test
    void chatRequestWithError_capturesErrorStatusAndException() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Trigger error"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        RuntimeException error = new RuntimeException("API rate limit exceeded");
        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                error,
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onError(errorContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify error status
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("API rate limit exceeded");

        // Verify error type attribute
        assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                .isEqualTo(RuntimeException.class.getName());

        // Verify exception event was recorded
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    }

    /**
     * Additional test: Verify different model providers are captured correctly
     */
    @Test
    void chatRequestWithDifferentProvider_capturesProviderSystem() {
        // Arrange
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello from Anthropic"))
                .modelName("claude-3-sonnet")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.ANTHROPIC,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello! I'm Claude."))
                .id("msg-001")
                .tokenUsage(new TokenUsage(15, 20))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                ModelProvider.ANTHROPIC,
                attributes
        );

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify Anthropic system
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM))
                .isEqualTo("anthropic");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .isEqualTo("claude-3-sonnet");
    }
}
