package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryChatModelListenerTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetry openTelemetry;
    private OpenTelemetryChatModelListener listener;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        listener = new OpenTelemetryChatModelListener(openTelemetry);
    }

    @Test
    void should_create_client_span_for_chat_request() {
        // Given
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .parameters(ChatRequestParameters.builder()
                        .model("gpt-4")
                        .temperature(0.7)
                        .maxTokens(100)
                        .build())
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi there"))
                .id("resp-123")
                .model("gpt-4")
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 20))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
        ChatModelResponseContext responseContext = new ChatModelResponseContext(request, response, attributes);

        // When
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("chat gpt-4");
        assertThat(span.getKind().name()).isEqualTo("CLIENT");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Check GenAI semantic convention attributes
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.system")))
                .isEqualTo("openai");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.request.model")))
                .isEqualTo("gpt-4");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.operation.name")))
                .isEqualTo("chat");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.doubleKey("gen_ai.request.temperature")))
                .isEqualTo(0.7);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey("gen_ai.request.max_tokens")))
                .isEqualTo(100L);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey("gen_ai.usage.input_tokens")))
                .isEqualTo(10L);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.longKey("gen_ai.usage.output_tokens")))
                .isEqualTo(20L);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.response.finish_reasons")))
                .isEqualTo("stop");
    }

    @Test
    void should_not_capture_content_by_default() {
        // Given
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("My SSN is 123-45-6789")))
                .parameters(ChatRequestParameters.builder()
                        .model("gpt-4")
                        .build())
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Sensitive data"))
                .build();
        ChatModelResponseContext responseContext = new ChatModelResponseContext(request, response, attributes);

        // When
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        // Content should NOT be captured by default
        assertThat(span.getAttributes().asMap()).doesNotContainKey(
                io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.request.content"));
        assertThat(span.getAttributes().asMap()).doesNotContainKey(
                io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.response.content"));
    }

    @Test
    void should_capture_and_truncate_content_when_enabled() {
        // Given
        OpenTelemetryChatModelListener listenerWithContent =
                new OpenTelemetryChatModelListener(openTelemetry, true, 20, false);

        String longContent = "a".repeat(100);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(longContent)))
                .parameters(ChatRequestParameters.builder()
                        .model("gpt-4")
                        .build())
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from(longContent))
                .build();
        ChatModelResponseContext responseContext = new ChatModelResponseContext(request, response, attributes);

        // When
        listenerWithContent.onRequest(requestContext);
        listenerWithContent.onResponse(responseContext);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        String capturedRequestContent = (String) span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.request.content"));
        String capturedResponseContent = (String) span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.response.content"));

        assertThat(capturedRequestContent).hasSize(23); // 20 + "..."
        assertThat(capturedRequestContent).endsWith("...");
        assertThat(capturedResponseContent).hasSize(23); // 20 + "..."
        assertThat(capturedResponseContent).endsWith("...");
    }

    @Test
    void should_record_error_with_exception() {
        // Given
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello")))
                .parameters(ChatRequestParameters.builder()
                        .model("gpt-4")
                        .build())
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, attributes);

        RuntimeException error = new RuntimeException("Timeout error");
        ChatModelErrorContext errorContext = new ChatModelErrorContext(request, error, null, attributes);

        // When
        listener.onRequest(requestContext);
        listener.onError(errorContext);

        // Then
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("Timeout error");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("error.type")))
                .isEqualTo("RuntimeException");
    }

    @Test
    void should_extract_system_from_model_name() {
        // Test various model names
        testModelSystemExtraction("gpt-4", "openai");
        testModelSystemExtraction("claude-3-opus", "anthropic");
        testModelSystemExtraction("gemini-pro", "google");
        testModelSystemExtraction("mistral-7b", "mistral");
        testModelSystemExtraction("llama-2", "meta");
    }

    private void testModelSystemExtraction(String model, String expectedSystem) {
        // Reset exporter
        spanExporter.reset();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("test")))
                .parameters(ChatRequestParameters.builder()
                        .model(model)
                        .build())
                .build();
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, attributes);

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("response"))
                .build();
        ChatModelResponseContext responseContext = new ChatModelResponseContext(request, response, attributes);

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.system")))
                .isEqualTo(expectedSystem);
    }
}
