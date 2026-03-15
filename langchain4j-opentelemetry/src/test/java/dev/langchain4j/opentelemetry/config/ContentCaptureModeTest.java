package dev.langchain4j.opentelemetry.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContentCaptureMode functionality.
 * <p>
 * This test class verifies the three content capture modes:
 * <ul>
 *   <li>FULL: Captures complete message content (user, system, assistant messages)</li>
 *   <li>METADATA: Captures message roles and counts, but not actual content</li>
 *   <li>NONE: Captures only model, tokens, and timing metadata</li>
 * </ul>
 */
class ContentCaptureModeTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private static final String INSTRUMENTATION_NAME = "dev.langchain4j.opentelemetry.test";

    /**
     * Test Case 1: FULL mode with user message 'What is 2+2?'
     * Expected: Span attribute gen_ai.request.user_message contains 'What is 2+2?'
     *
     * This test verifies that FULL content capture mode captures the complete
     * user message content in the span attributes.
     */
    @Test
    void fullMode_capturesUserMessageContent() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.FULL;

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2?"))
                .modelName("gpt-4")
                .build();

        // Build attributes with content capture
        AttributesBuilder attrBuilder = Attributes.builder();
        attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MODEL, "gpt-4");
        attrBuilder.put(GenAiAttributes.GEN_AI_SYSTEM, "openai");

        // Apply content capture helper
        ContentCaptureHelper.addRequestAttributes(attrBuilder, request, mode);

        // Create span with attributes
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate LLM operation
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify user message content is captured
        String userMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE);
        assertThat(userMessage)
                .as("FULL mode should capture user message content")
                .isNotNull()
                .contains("What is 2+2?");

        // Verify message count is also captured
        Long messageCount = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_COUNT);
        assertThat(messageCount)
                .as("FULL mode should capture message count")
                .isEqualTo(1L);
    }

    /**
     * Test Case 2: METADATA mode with user message
     * Expected: Span contains gen_ai.request.message_count=1, no message content
     *
     * This test verifies that METADATA capture mode captures only message
     * metadata (count, roles) without the actual message content.
     */
    @Test
    void metadataMode_capturesMessageCountWithoutContent() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.METADATA;

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("This message content should NOT be captured"))
                .modelName("gpt-4")
                .build();

        // Build attributes with content capture
        AttributesBuilder attrBuilder = Attributes.builder();
        attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MODEL, "gpt-4");

        // Apply content capture helper
        ContentCaptureHelper.addRequestAttributes(attrBuilder, request, mode);

        // Create span with attributes
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate LLM operation
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify message count IS captured
        Long messageCount = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_COUNT);
        assertThat(messageCount)
                .as("METADATA mode should capture message count")
                .isEqualTo(1L);

        // Verify message roles are captured
        List<String> messageRoles = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_ROLES);
        assertThat(messageRoles)
                .as("METADATA mode should capture message roles")
                .isNotNull()
                .contains("user");

        // Verify message content is NOT captured
        String userMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE);
        assertThat(userMessage)
                .as("METADATA mode should NOT capture user message content")
                .isNull();

        String systemMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_SYSTEM_MESSAGE);
        assertThat(systemMessage)
                .as("METADATA mode should NOT capture system message content")
                .isNull();
    }

    /**
     * Test Case 3: NONE mode with sensitive data
     * Expected: Span contains only gen_ai.usage.* and timing attributes
     *
     * This test verifies that NONE capture mode does not capture any message
     * content or metadata, only basic model and usage information.
     */
    @Test
    void noneMode_capturesOnlyUsageAndTimingAttributes() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.NONE;

        // Create request with sensitive data that should NOT be captured
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("SECRET API KEY: sk-1234567890"),
                        UserMessage.from("Password: super_secret_123")
                )
                .modelName("gpt-4")
                .build();

        // Build attributes - only add model and usage info (what NONE mode should capture)
        AttributesBuilder attrBuilder = Attributes.builder();
        attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MODEL, "gpt-4");
        attrBuilder.put(GenAiAttributes.GEN_AI_SYSTEM, "openai");

        // Apply content capture helper - should add nothing in NONE mode
        ContentCaptureHelper.addRequestAttributes(attrBuilder, request, mode);

        // Simulate adding usage attributes (typically added on response)
        attrBuilder.put(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, 50L);
        attrBuilder.put(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, 25L);
        attrBuilder.put(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS, 75L);

        // Create span with attributes
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate LLM operation
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify usage attributes ARE present
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS))
                .as("NONE mode should still capture token usage")
                .isEqualTo(50L);
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS))
                .as("NONE mode should still capture token usage")
                .isEqualTo(25L);
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS))
                .as("NONE mode should still capture token usage")
                .isEqualTo(75L);

        // Verify model attribute IS present
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .as("NONE mode should still capture model")
                .isEqualTo("gpt-4");

        // Verify NO content or metadata is captured
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE))
                .as("NONE mode should NOT capture user message")
                .isNull();
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_SYSTEM_MESSAGE))
                .as("NONE mode should NOT capture system message")
                .isNull();
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_COUNT))
                .as("NONE mode should NOT capture message count")
                .isNull();
        assertThat(spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_ROLES))
                .as("NONE mode should NOT capture message roles")
                .isNull();
    }

    /**
     * Test Case 4: FULL mode with assistant response
     * Expected: Span attribute gen_ai.response.content contains response text
     *
     * This test verifies that FULL content capture mode captures the complete
     * assistant response content in the span attributes.
     */
    @Test
    void fullMode_capturesAssistantResponseContent() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.FULL;

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is 2+2?"))
                .modelName("gpt-4")
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("2+2 equals 4."))
                .id("test-response-id")
                .tokenUsage(new TokenUsage(10, 20))
                .finishReason(FinishReason.STOP)
                .build();

        // Build attributes with request content capture
        AttributesBuilder attrBuilder = Attributes.builder();
        attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MODEL, "gpt-4");
        attrBuilder.put(GenAiAttributes.GEN_AI_SYSTEM, "openai");

        ContentCaptureHelper.addRequestAttributes(attrBuilder, request, mode);

        // Create span with initial attributes
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Add response attributes
            AttributesBuilder responseAttrBuilder = Attributes.builder();
            ContentCaptureHelper.addResponseAttributes(responseAttrBuilder, response, mode);

            // Apply response attributes to span
            Attributes responseAttrs = responseAttrBuilder.build();
            responseAttrs.forEach((key, value) -> {
                span.setAttribute((io.opentelemetry.api.common.AttributeKey<Object>) key, value);
            });

            // Add token usage
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, 10L);
            span.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, 20L);
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify response content is captured
        String responseContent = spanAttributes.get(GenAiAttributes.GEN_AI_RESPONSE_CONTENT);
        assertThat(responseContent)
                .as("FULL mode should capture assistant response content")
                .isNotNull()
                .contains("2+2 equals 4");

        // Verify user message is also captured
        String userMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE);
        assertThat(userMessage)
                .as("FULL mode should also capture user message")
                .isNotNull()
                .contains("What is 2+2?");
    }

    /**
     * Additional test: Verify METADATA mode does NOT capture response content
     */
    @Test
    void metadataMode_doesNotCaptureResponseContent() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.METADATA;

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("This response content should NOT be captured"))
                .id("test-response-id")
                .tokenUsage(new TokenUsage(10, 20))
                .finishReason(FinishReason.STOP)
                .build();

        AttributesBuilder attrBuilder = Attributes.builder();
        ContentCaptureHelper.addResponseAttributes(attrBuilder, response, mode);

        // Create span
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate LLM operation
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify response content is NOT captured in METADATA mode
        String responseContent = spanAttributes.get(GenAiAttributes.GEN_AI_RESPONSE_CONTENT);
        assertThat(responseContent)
                .as("METADATA mode should NOT capture response content")
                .isNull();
    }

    /**
     * Additional test: Verify FULL mode with multiple messages
     */
    @Test
    void fullMode_handlesMultipleMessages() {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer(INSTRUMENTATION_NAME);
        ContentCaptureMode mode = ContentCaptureMode.FULL;

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("You are a helpful assistant."),
                        UserMessage.from("Hello!"),
                        UserMessage.from("How are you?")
                )
                .modelName("gpt-4")
                .build();

        AttributesBuilder attrBuilder = Attributes.builder();
        ContentCaptureHelper.addRequestAttributes(attrBuilder, request, mode);

        // Create span
        Span span = tracer.spanBuilder(GenAiSpanNames.CHAT)
                .setSpanKind(SpanKind.CLIENT)
                .setAllAttributes(attrBuilder.build())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Simulate LLM operation
        } finally {
            span.end();
        }

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData spanData = spans.get(0);
        Attributes spanAttributes = spanData.getAttributes();

        // Verify all messages are captured
        Long messageCount = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_COUNT);
        assertThat(messageCount)
                .as("Should count all messages")
                .isEqualTo(3L);

        String userMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE);
        assertThat(userMessage)
                .as("Should concatenate user messages")
                .contains("Hello!")
                .contains("How are you?");

        String systemMessage = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_SYSTEM_MESSAGE);
        assertThat(systemMessage)
                .as("Should capture system message")
                .contains("You are a helpful assistant");

        List<String> roles = spanAttributes.get(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_ROLES);
        assertThat(roles)
                .as("Should capture all message roles")
                .hasSize(3)
                .contains("system", "user");
    }

    /**
     * Test: Configuration builder correctly sets content capture mode
     */
    @Test
    void configBuilder_setsContentCaptureMode() {
        // Test FULL mode (default)
        OpenTelemetryLangChain4jConfig defaultConfig = OpenTelemetryLangChain4jConfig.defaultConfig();
        assertThat(defaultConfig.getContentCaptureMode())
                .as("Default content capture mode should be FULL")
                .isEqualTo(ContentCaptureMode.FULL);

        // Test METADATA mode
        OpenTelemetryLangChain4jConfig metadataConfig = OpenTelemetryLangChain4jConfig.builder()
                .contentCaptureMode(ContentCaptureMode.METADATA)
                .build();
        assertThat(metadataConfig.getContentCaptureMode())
                .as("Config should reflect METADATA mode")
                .isEqualTo(ContentCaptureMode.METADATA);

        // Test NONE mode
        OpenTelemetryLangChain4jConfig noneConfig = OpenTelemetryLangChain4jConfig.builder()
                .contentCaptureMode(ContentCaptureMode.NONE)
                .build();
        assertThat(noneConfig.getContentCaptureMode())
                .as("Config should reflect NONE mode")
                .isEqualTo(ContentCaptureMode.NONE);
    }

    /**
     * Test: Null handling in ContentCaptureHelper
     */
    @Test
    void contentCaptureHelper_handlesNullGracefully() {
        AttributesBuilder attrBuilder = Attributes.builder();

        // Should not throw with null mode
        ContentCaptureHelper.addRequestAttributes(attrBuilder,
                ChatRequest.builder().messages(UserMessage.from("test")).build(),
                null);

        // Should not throw with null response
        ContentCaptureHelper.addResponseAttributes(attrBuilder,
                ChatResponse.builder().aiMessage(AiMessage.from("")).build(),
                null);

        // Verify attributes are empty
        Attributes attrs = attrBuilder.build();
        assertThat(attrs.get(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE))
                .as("Null mode should result in no content capture")
                .isNull();
    }

    /**
     * Test: ContentCaptureMode enum values
     */
    @Test
    void contentCaptureMode_hasExpectedValues() {
        ContentCaptureMode[] modes = ContentCaptureMode.values();

        assertThat(modes)
                .as("ContentCaptureMode should have exactly 3 values")
                .hasSize(3)
                .containsExactly(ContentCaptureMode.FULL, ContentCaptureMode.METADATA, ContentCaptureMode.NONE);
    }
}
