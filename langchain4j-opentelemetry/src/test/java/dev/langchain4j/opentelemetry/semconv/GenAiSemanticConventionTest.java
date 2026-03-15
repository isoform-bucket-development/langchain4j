package dev.langchain4j.opentelemetry.semconv;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying compliance with OpenTelemetry GenAI semantic conventions.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI Semantic Conventions</a>
 */
class GenAiSemanticConventionTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    // ==================== Attribute Key Tests ====================

    @Test
    @DisplayName("GEN_AI_SYSTEM attribute key follows semantic convention")
    void genAiSystemAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_SYSTEM.getKey())
                .isEqualTo("gen_ai.system");
        assertThat(GenAiAttributes.GEN_AI_SYSTEM.getType().name())
                .isEqualTo("STRING");
    }

    @Test
    @DisplayName("GEN_AI_REQUEST_MODEL attribute key follows semantic convention")
    void genAiRequestModelAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MODEL.getKey())
                .isEqualTo("gen_ai.request.model");
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MODEL.getType().name())
                .isEqualTo("STRING");
    }

    @Test
    @DisplayName("GEN_AI_REQUEST_TEMPERATURE attribute key follows semantic convention")
    void genAiRequestTemperatureAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE.getKey())
                .isEqualTo("gen_ai.request.temperature");
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE.getType().name())
                .isEqualTo("DOUBLE");
    }

    @Test
    @DisplayName("GEN_AI_REQUEST_TOP_P attribute key follows semantic convention")
    void genAiRequestTopPAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TOP_P.getKey())
                .isEqualTo("gen_ai.request.top_p");
        assertThat(GenAiAttributes.GEN_AI_REQUEST_TOP_P.getType().name())
                .isEqualTo("DOUBLE");
    }

    @Test
    @DisplayName("GEN_AI_REQUEST_MAX_TOKENS attribute key follows semantic convention")
    void genAiRequestMaxTokensAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS.getKey())
                .isEqualTo("gen_ai.request.max_tokens");
        assertThat(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS.getType().name())
                .isEqualTo("LONG");
    }

    @Test
    @DisplayName("GEN_AI_RESPONSE_ID attribute key follows semantic convention")
    void genAiResponseIdAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_ID.getKey())
                .isEqualTo("gen_ai.response.id");
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_ID.getType().name())
                .isEqualTo("STRING");
    }

    @Test
    @DisplayName("GEN_AI_RESPONSE_FINISH_REASONS attribute key follows semantic convention")
    void genAiResponseFinishReasonsAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS.getKey())
                .isEqualTo("gen_ai.response.finish_reasons");
        assertThat(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS.getType().name())
                .isEqualTo("STRING_ARRAY");
    }

    @Test
    @DisplayName("GEN_AI_USAGE_INPUT_TOKENS attribute key follows semantic convention")
    void genAiUsageInputTokensAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS.getKey())
                .isEqualTo("gen_ai.usage.input_tokens");
        assertThat(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS.getType().name())
                .isEqualTo("LONG");
    }

    @Test
    @DisplayName("GEN_AI_USAGE_OUTPUT_TOKENS attribute key follows semantic convention")
    void genAiUsageOutputTokensAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS.getKey())
                .isEqualTo("gen_ai.usage.output_tokens");
        assertThat(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS.getType().name())
                .isEqualTo("LONG");
    }

    @Test
    @DisplayName("GEN_AI_OPERATION_NAME attribute key follows semantic convention")
    void genAiOperationNameAttributeKey() {
        assertThat(GenAiAttributes.GEN_AI_OPERATION_NAME.getKey())
                .isEqualTo("gen_ai.operation.name");
        assertThat(GenAiAttributes.GEN_AI_OPERATION_NAME.getType().name())
                .isEqualTo("STRING");
    }

    // ==================== Test Case 1: OpenAI Provider Attributes ====================

    @Test
    @DisplayName("Test Case 1: OpenAI provider with gpt-4o model sets correct attributes")
    void testCase1_openAiProviderWithGpt4oModel() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi there!"))
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(10, 5))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify gen_ai.system = 'open_ai' (lowercased ModelProvider name)
        String system = span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM);
        assertThat(system).isEqualTo("open_ai");

        // Verify gen_ai.request.model = 'gpt-4o'
        String model = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
        assertThat(model).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("Test Case 1b: OpenAI provider sets correct operation name")
    void testCase1b_openAiProviderSetsOperationName() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        String operationName = span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME);
        assertThat(operationName).isEqualTo("chat");
    }

    // ==================== Test Case 2: Anthropic Provider Attributes ====================

    @Test
    @DisplayName("Test Case 2: Anthropic provider with claude-3-5-sonnet model sets correct attributes")
    void testCase2_anthropicProviderWithClaude35Sonnet() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("claude-3-5-sonnet")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.ANTHROPIC, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello! How can I help?"))
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(15, 8))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.ANTHROPIC, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify gen_ai.system = 'anthropic'
        String system = span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM);
        assertThat(system).isEqualTo("anthropic");

        // Verify gen_ai.request.model = 'claude-3-5-sonnet'
        String model = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
        assertThat(model).isEqualTo("claude-3-5-sonnet");
    }

    // ==================== Test Case 3: Request Parameters ====================

    @Test
    @DisplayName("Test Case 3: Request with temperature=0.9, top_p=0.95, max_tokens=1000 sets correct attributes")
    void testCase3_requestWithAllParameters() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .temperature(0.9)
                .topP(0.95)
                .maxOutputTokens(1000)
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify gen_ai.request.temperature = 0.9
        Double temperature = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE);
        assertThat(temperature).isEqualTo(0.9);

        // Verify gen_ai.request.top_p = 0.95
        Double topP = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TOP_P);
        assertThat(topP).isEqualTo(0.95);

        // Verify gen_ai.request.max_tokens = 1000
        Long maxTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS);
        assertThat(maxTokens).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Test Case 3b: Request with only temperature sets only temperature attribute")
    void testCase3b_requestWithOnlyTemperature() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .temperature(0.7)
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        Double temperature = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE);
        assertThat(temperature).isEqualTo(0.7);

        // top_p and max_tokens should be null when not set
        Double topP = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TOP_P);
        assertThat(topP).isNull();

        Long maxTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS);
        assertThat(maxTokens).isNull();
    }

    // ==================== Test Case 4: Response Attributes ====================

    @Test
    @DisplayName("Test Case 4: Response with id='chatcmpl-123' and finish reasons sets correct attributes")
    void testCase4_responseWithIdAndFinishReasons() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .id("chatcmpl-123")
                .aiMessage(AiMessage.from("Response"))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify gen_ai.response.id = 'chatcmpl-123'
        String responseId = span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_ID);
        assertThat(responseId).isEqualTo("chatcmpl-123");

        // Verify gen_ai.response.finish_reasons
        List<String> finishReasons = span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS);
        assertThat(finishReasons).containsExactly("STOP");
    }

    @Test
    @DisplayName("Test Case 4b: Response with LENGTH finish reason sets correct attribute")
    void testCase4b_responseWithLengthFinishReason() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response"))
                .finishReason(FinishReason.LENGTH)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        List<String> finishReasons = span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS);
        assertThat(finishReasons).containsExactly("LENGTH");
    }

    @Test
    @DisplayName("Test Case 4c: Response with token usage sets correct attributes")
    void testCase4c_responseWithTokenUsage() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response"))
                .tokenUsage(new TokenUsage(50, 100))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        Long inputTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS);
        assertThat(inputTokens).isEqualTo(50L);

        Long outputTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS);
        assertThat(outputTokens).isEqualTo(100L);

        Long totalTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS);
        assertThat(totalTokens).isEqualTo(150L);
    }

    // ==================== Test Case 5: Span Naming Conventions ====================

    @Test
    @DisplayName("Test Case 5: Chat operations use 'gen_ai.chat' span name constant")
    void testCase5_chatSpanNameConstant() {
        // Verify constant value
        assertThat(GenAiSpanNames.CHAT).isEqualTo("gen_ai.chat");
    }

    @Test
    @DisplayName("Test Case 5b: Content completion operations use 'gen_ai.content.completion' span name")
    void testCase5b_contentCompletionSpanName() {
        // Verify constant value
        assertThat(GenAiSpanNames.CONTENT_COMPLETION).isEqualTo("gen_ai.content.completion");
    }

    @Test
    @DisplayName("Test Case 5c: Tool execution span names follow 'tool.execution.{toolName}' format")
    void testCase5c_toolExecutionSpanName() {
        String spanName = GenAiSpanNames.toolExecution("searchWeb");
        assertThat(spanName).isEqualTo("tool.execution.searchWeb");
    }

    @Test
    @DisplayName("Test Case 5d: Input guardrail span names follow 'guardrail.input.{name}' format")
    void testCase5d_inputGuardrailSpanName() {
        String spanName = GenAiSpanNames.guardrailInput("contentFilter");
        assertThat(spanName).isEqualTo("guardrail.input.contentFilter");
    }

    @Test
    @DisplayName("Test Case 5e: Output guardrail span names follow 'guardrail.output.{name}' format")
    void testCase5e_outputGuardrailSpanName() {
        String spanName = GenAiSpanNames.guardrailOutput("responseValidator");
        assertThat(spanName).isEqualTo("guardrail.output.responseValidator");
    }

    @Test
    @DisplayName("Test Case 5f: AiService method span names follow 'AiService.{methodName}' format")
    void testCase5f_aiServiceMethodSpanName() {
        String spanName = GenAiSpanNames.aiServiceMethod("summarize");
        assertThat(spanName).isEqualTo("AiService.summarize");
    }

    @Test
    @DisplayName("Test Case 5g: Chat span is created with correct name 'gen_ai.chat'")
    void testCase5g_chatSpanIsCreatedWithCorrectName() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo("gen_ai.chat");
    }

    @Test
    @DisplayName("Test Case 5h: Chat span is created with CLIENT span kind")
    void testCase5h_chatSpanHasClientKind() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getKind()).isEqualTo(SpanKind.CLIENT);
    }

    // ==================== Additional Provider Tests ====================

    @Test
    @DisplayName("Ollama provider sets correct system attribute")
    void ollamaProviderSetsCorrectSystemAttribute() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("llama3")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OLLAMA, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OLLAMA, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        String system = span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM);
        assertThat(system).isEqualTo("ollama");
    }

    @Test
    @DisplayName("Azure OpenAI provider sets correct system attribute")
    void azureOpenAiProviderSetsCorrectSystemAttribute() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.AZURE_OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.AZURE_OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        String system = span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM);
        assertThat(system).isEqualTo("azure_open_ai");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Request without model name should not set model attribute")
    void requestWithoutModelName() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        String model = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
        assertThat(model).isNull();
    }

    @Test
    @DisplayName("Request without provider should not set system attribute")
    void requestWithoutProvider() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, null, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, null, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        String system = span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM);
        assertThat(system).isNull();
    }

    @Test
    @DisplayName("Response without token usage should not set token attributes")
    void responseWithoutTokenUsage() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        Long inputTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS);
        assertThat(inputTokens).isNull();

        Long outputTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS);
        assertThat(outputTokens).isNull();
    }

    @Test
    @DisplayName("Zero values for temperature should be captured")
    void zeroTemperatureValue() {
        // Arrange
        ChatModelListener listener = createListener();
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .modelName("gpt-4o")
                .temperature(0.0)
                .build();

        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request, ModelProvider.OPEN_AI, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi!"))
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response, request, ModelProvider.OPEN_AI, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        Double temperature = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE);
        assertThat(temperature).isEqualTo(0.0);
    }

    // ==================== Helper Methods ====================

    private ChatModelListener createListener() {
        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .build();
    }
}
