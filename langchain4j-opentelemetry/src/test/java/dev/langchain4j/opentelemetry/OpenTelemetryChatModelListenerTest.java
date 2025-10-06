package dev.langchain4j.opentelemetry;

import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test suite for OpenTelemetryChatModelListener.
 * These tests are designed to fail initially (TDD red phase) and validate:
 * 1. ChatModel requests create CLIENT spans
 * 2. GenAI semantic convention attributes are properly set
 * 3. Token usage metrics are captured
 */
class OpenTelemetryChatModelListenerTest {

    private OpenTelemetry openTelemetry;
    private InMemorySpanExporter spanExporter;
    private OpenTelemetryChatModelListener listener;

    @Mock
    private ChatModelRequestContext requestContext;
    @Mock
    private ChatModelResponseContext responseContext;
    @Mock
    private ChatModelErrorContext errorContext;
    @Mock
    private ChatRequest chatRequest;
    @Mock
    private ChatResponse chatResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        spanExporter = InMemorySpanExporter.create();

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build())
            .build();

        // This will fail because OpenTelemetryChatModelListener doesn't exist yet
        try {
            listener = new OpenTelemetryChatModelListener(openTelemetry.getTracer("langchain4j"));
        } catch (Exception e) {
            fail("OpenTelemetryChatModelListener class not yet implemented: " + e.getMessage());
        }
    }

    @Test
    void shouldCreateClientSpanOnChatModelRequest() {
        // This test will fail because the onRequest implementation doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");

        listener.onRequest(requestContext);

        assertEquals(1, spanExporter.getFinishedSpanItems().size(),
            "Should create CLIENT span for ChatModel request");

        var span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals("chat_model.generate", span.getName(),
            "Span should have 'chat_model.generate' operation name");
        assertEquals(SpanKind.CLIENT, span.getKind(),
            "ChatModel span should be CLIENT span kind");
    }

    @Test
    void shouldSetGenAiSystemAttribute() {
        // This test will fail because GenAI attribute extraction doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);
        String genAiSystem = span.getAttributes().get(AttributeKey.stringKey("gen_ai.system"));

        assertNotNull(genAiSystem, "Should set gen_ai.system attribute");
        assertEquals("openai", genAiSystem, "Should extract correct provider from model name");
    }

    @Test
    void shouldSetGenAiRequestModelAttribute() {
        // This test will fail because model attribute handling doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);
        String requestModel = span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model"));

        assertNotNull(requestModel, "Should set gen_ai.request.model attribute");
        assertEquals("gpt-4", requestModel, "Should capture exact model name from request");
    }

    @Test
    void shouldSetGenAiRequestParametersAttributes() {
        // This test will fail because parameter extraction doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-3.5-turbo");
        when(chatRequest.temperature()).thenReturn(0.7);
        when(chatRequest.maxTokens()).thenReturn(1000);
        when(chatRequest.topP()).thenReturn(0.9);

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);

        Double temperature = span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.temperature"));
        Integer maxTokens = span.getAttributes().get(AttributeKey.longKey("gen_ai.request.max_tokens"));
        Double topP = span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.top_p"));

        assertEquals(0.7, temperature, "Should capture temperature parameter");
        assertEquals(1000, maxTokens, "Should capture max_tokens parameter");
        assertEquals(0.9, topP, "Should capture top_p parameter");
    }

    @Test
    void shouldCaptureTokenUsageOnResponse() {
        // This test will fail because token usage capture doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");

        when(responseContext.request()).thenReturn(chatRequest);
        when(responseContext.response()).thenReturn(chatResponse);
        when(chatResponse.tokenUsage()).thenReturn(createMockTokenUsage(150, 50));

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        var span = spanExporter.getFinishedSpanItems().get(0);

        Integer inputTokens = span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens"));
        Integer outputTokens = span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens"));

        assertEquals(150, inputTokens, "Should capture input token usage");
        assertEquals(50, outputTokens, "Should capture output token usage");
    }

    @Test
    void shouldRedactContentByDefault() {
        // This test will fail because content redaction logic doesn't exist
        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");
        when(chatRequest.toString()).thenReturn("User prompt with sensitive data: SSN 123-45-6789");

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);

        // gen_ai.prompt should either be absent or redacted by default
        String prompt = span.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt"));
        assertTrue(prompt == null || prompt.equals("[REDACTED]"),
            "Should redact content by default for privacy");
    }

    @Test
    void shouldCaptureContentWhenExplicitlyEnabled() {
        // This test will fail because content capture configuration doesn't exist
        // Simulate enabling content capture
        try {
            listener.setContentCaptureEnabled(true);
        } catch (Exception e) {
            fail("Content capture configuration not yet implemented: " + e.getMessage());
        }

        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");
        when(chatRequest.toString()).thenReturn("What is the capital of France?");

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);
        String prompt = span.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt"));

        assertNotNull(prompt, "Should capture content when explicitly enabled");
        assertTrue(prompt.contains("capital of France"), "Should contain actual prompt content");
    }

    @Test
    void shouldTruncateContentWhenOverLimit() {
        // This test will fail because content truncation doesn't exist
        try {
            listener.setContentCaptureEnabled(true);
            listener.setMaxContentLength(20);
        } catch (Exception e) {
            fail("Content truncation configuration not yet implemented: " + e.getMessage());
        }

        String longPrompt = "This is a very long prompt that should be truncated at the configured limit";

        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");
        when(chatRequest.toString()).thenReturn(longPrompt);

        listener.onRequest(requestContext);

        var span = spanExporter.getFinishedSpanItems().get(0);
        String prompt = span.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt"));

        assertTrue(prompt.length() <= 23, // 20 chars + "..." = 23
            "Should truncate content over limit, got length: " + prompt.length());
        assertTrue(prompt.endsWith("..."), "Truncated content should end with '...'");
    }

    @Test
    void shouldHandleChatModelErrors() {
        // This test will fail because error handling doesn't exist
        Exception testError = new RuntimeException("Model request failed");

        when(requestContext.request()).thenReturn(chatRequest);
        when(chatRequest.modelName()).thenReturn("gpt-4");

        when(errorContext.request()).thenReturn(chatRequest);
        when(errorContext.error()).thenReturn(testError);

        listener.onRequest(requestContext);
        listener.onError(errorContext);

        var span = spanExporter.getFinishedSpanItems().get(0);

        assertTrue(span.getStatus().isError(), "Span should be marked as error");

        String errorType = span.getAttributes().get(AttributeKey.stringKey("error.type"));
        assertEquals("RuntimeException", errorType, "Should capture error type");
    }

    // Mock helper method - this will fail because TokenUsage interface may not exist or have different structure
    private Object createMockTokenUsage(int inputTokens, int outputTokens) {
        try {
            // Attempt to create a mock TokenUsage object
            // This will fail because we don't know the exact interface yet
            return new Object() {
                public int getInputTokenCount() { return inputTokens; }
                public int getOutputTokenCount() { return outputTokens; }
            };
        } catch (Exception e) {
            fail("TokenUsage interface structure unknown: " + e.getMessage());
            return null;
        }
    }
}