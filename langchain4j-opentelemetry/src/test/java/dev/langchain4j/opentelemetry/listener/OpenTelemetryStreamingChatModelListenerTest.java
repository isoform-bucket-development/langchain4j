package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenTelemetryChatModelListener with streaming chat model operations.
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>Span generation for streaming operations with proper timing</li>
 *   <li>Context propagation from parent spans across async callbacks</li>
 *   <li>Error handling for interrupted streaming operations</li>
 * </ul>
 */
class OpenTelemetryStreamingChatModelListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener listener;

    @BeforeEach
    void setUp() {
        listener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(true)
                .build();
    }

    /**
     * Test Case 1: Streaming request returning 5 token chunks.
     * Expected: Single span containing all token usage after stream completion.
     */
    @Test
    void streamingRequestWithFiveTokenChunks_generatesSpanWithTotalTokenUsage() throws Exception {
        // Arrange
        int totalChunks = 5;
        int inputTokens = 10;
        int outputTokens = 25;

        TestStreamingChatModel chatModel = new TestStreamingChatModel(
                List.of(listener),
                totalChunks,
                inputTokens,
                outputTokens,
                false // no error
        );

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello, how are you?"))
                .modelName("gpt-4")
                .temperature(0.7)
                .build();

        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicInteger partialResponseCount = new AtomicInteger(0);

        // Act
        chatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponseCount.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                completionLatch.countDown();
            }
        });

        // Wait for stream completion
        assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Assert - verify we received all chunks
        assertThat(partialResponseCount.get()).isEqualTo(totalChunks);

        // Assert - verify span was created with correct attributes
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo(GenAiSpanNames.CHAT);
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify token usage is captured after stream completion
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS))
                .isEqualTo((long) inputTokens);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS))
                .isEqualTo((long) outputTokens);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_TOTAL_TOKENS))
                .isEqualTo((long) (inputTokens + outputTokens));

        // Verify streaming flag is set
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING)).isTrue();

        // Verify span ended after stream completion (has end time)
        assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
    }

    /**
     * Test Case 2: Streaming request with context from parent span.
     * Expected: Streaming span is child of parent span, preserving trace ID.
     */
    @Test
    void streamingRequestWithParentSpan_preservesTraceContext() throws Exception {
        // Arrange
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test-tracer");

        TestStreamingChatModel chatModel = new TestStreamingChatModel(
                List.of(listener),
                3, // chunks
                5, // input tokens
                15, // output tokens
                false // no error
        );

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"))
                .modelName("claude-3")
                .build();

        CountDownLatch completionLatch = new CountDownLatch(1);

        // Act - create parent span and make request within its context
        Span parentSpan = tracer.spanBuilder("parent-operation")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope scope = parentSpan.makeCurrent()) {
            chatModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // Process partial response
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    completionLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completionLatch.countDown();
                }
            });

            // Wait for completion
            assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            parentSpan.end();
        }

        // Assert - verify parent-child relationship
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(2);

        SpanData parentSpanData = spans.stream()
                .filter(s -> s.getName().equals("parent-operation"))
                .findFirst()
                .orElseThrow();

        SpanData childSpanData = spans.stream()
                .filter(s -> s.getName().equals(GenAiSpanNames.CHAT))
                .findFirst()
                .orElseThrow();

        // Verify trace ID is preserved
        assertThat(childSpanData.getTraceId()).isEqualTo(parentSpanData.getTraceId());

        // Verify parent-child relationship
        assertThat(childSpanData.getParentSpanId()).isEqualTo(parentSpanData.getSpanId());

        // Verify child span attributes
        assertThat(childSpanData.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .isEqualTo("claude-3");
        assertThat(childSpanData.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING)).isTrue();
    }

    /**
     * Test Case 3: Streaming request interrupted mid-stream.
     * Expected: Span captures partial token count and error status.
     */
    @Test
    void streamingRequestInterruptedMidStream_capturesPartialTokensAndErrorStatus() throws Exception {
        // Arrange
        int chunksBeforeError = 3;
        int inputTokens = 10;
        int partialOutputTokens = 12; // Partial tokens before interruption

        TestStreamingChatModel chatModel = new TestStreamingChatModel(
                List.of(listener),
                chunksBeforeError,
                inputTokens,
                partialOutputTokens,
                true // simulate error
        );

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Generate a long response"))
                .modelName("gpt-4-turbo")
                .maxOutputTokens(1000)
                .build();

        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicInteger partialResponseCount = new AtomicInteger(0);
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        // Act
        chatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponseCount.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorFuture.complete(error);
                completionLatch.countDown();
            }
        });

        // Wait for error
        assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Assert - verify error was received
        Throwable capturedError = errorFuture.get(1, TimeUnit.SECONDS);
        assertThat(capturedError).isInstanceOf(RuntimeException.class);
        assertThat(capturedError.getMessage()).contains("Stream interrupted");

        // Assert - verify partial responses were received before error
        assertThat(partialResponseCount.get()).isEqualTo(chunksBeforeError);

        // Assert - verify span was created with error status
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo(GenAiSpanNames.CHAT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

        // Verify error attributes
        assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                .isEqualTo(RuntimeException.class.getName());

        // Verify request attributes were captured
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4-turbo");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS))
                .isEqualTo(1000L);

        // Verify exception was recorded
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");

        // Verify span ended (has valid end time)
        assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
    }

    /**
     * Test streaming chat model that simulates async token emission.
     */
    private static class TestStreamingChatModel implements StreamingChatModel {
        private final List<ChatModelListener> listeners;
        private final int numberOfChunks;
        private final int inputTokens;
        private final int outputTokens;
        private final boolean simulateError;

        TestStreamingChatModel(List<ChatModelListener> listeners, int numberOfChunks,
                               int inputTokens, int outputTokens, boolean simulateError) {
            this.listeners = listeners;
            this.numberOfChunks = numberOfChunks;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.simulateError = simulateError;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            // Simulate async streaming operation
            CompletableFuture.runAsync(() -> {
                try {
                    // Emit token chunks
                    for (int i = 0; i < numberOfChunks; i++) {
                        Thread.sleep(10); // Simulate network delay
                        handler.onPartialResponse("token" + i + " ");
                    }

                    if (simulateError) {
                        // Simulate stream interruption
                        throw new RuntimeException("Stream interrupted mid-operation");
                    }

                    // Complete successfully
                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Complete response"))
                            .id("response-123")
                            .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                            .finishReason(FinishReason.STOP)
                            .build();

                    handler.onCompleteResponse(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handler.onError(e);
                } catch (Exception e) {
                    handler.onError(e);
                }
            });
        }
    }
}
