package dev.langchain4j.opentelemetry.concurrency;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thread safety and concurrency tests for OpenTelemetry instrumentation.
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>No race conditions when handling 100 concurrent requests from 10 threads</li>
 *   <li>Each streaming request maintains its own context throughout</li>
 *   <li>No deadlocks or context corruption with mixed sync and async requests</li>
 *   <li>Span attributes are not mixed across concurrent requests</li>
 * </ul>
 */
class ThreadSafetyTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private OpenTelemetryChatModelListener syncListener;
    private OpenTelemetryChatModelListener streamingListener;

    @BeforeEach
    void setUp() {
        syncListener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(false)
                .build();

        streamingListener = OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .streaming(true)
                .build();
    }

    /**
     * Test Case 1: 100 concurrent requests from 10 threads.
     * Expected: 100 independent spans with no attribute mixing.
     * <p>
     * Each request should have its own span with a unique request ID attribute,
     * and no attribute should leak between concurrent requests.
     */
    @Test
    void hundredConcurrentRequestsFromTenThreads_generatesIndependentSpans() throws Exception {
        // Arrange
        final int totalRequests = 100;
        final int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(totalRequests);
        CyclicBarrier barrier = new CyclicBarrier(threadPoolSize);

        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Act - submit all requests
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            final String uniqueModel = "model-" + requestId;
            final int uniqueTokenCount = 100 + requestId;

            Future<Boolean> future = executor.submit(() -> {
                try {
                    // Wait for all threads to be ready (first 10 threads use barrier)
                    if (requestId < threadPoolSize) {
                        barrier.await(5, TimeUnit.SECONDS);
                    }
                    startLatch.await(5, TimeUnit.SECONDS);

                    // Create request with unique identifiers
                    ChatRequest request = ChatRequest.builder()
                            .messages(UserMessage.from("Request " + requestId))
                            .modelName(uniqueModel)
                            .temperature(0.7 + (requestId * 0.001)) // Unique temperature
                            .build();

                    // Each request gets its own attributes map (per-request isolation)
                    Map<Object, Object> attributes = new ConcurrentHashMap<>();
                    attributes.put("request.id", requestId);

                    ChatModelRequestContext requestContext = new ChatModelRequestContext(
                            request,
                            ModelProvider.OPEN_AI,
                            attributes
                    );

                    // Start the span
                    syncListener.onRequest(requestContext);

                    // Simulate some processing time with random variation
                    Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));

                    // Create response with unique token usage
                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Response " + requestId))
                            .id("response-" + requestId)
                            .tokenUsage(new TokenUsage(uniqueTokenCount, uniqueTokenCount + 50))
                            .finishReason(FinishReason.STOP)
                            .build();

                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            response,
                            request,
                            ModelProvider.OPEN_AI,
                            attributes
                    );

                    // End the span
                    syncListener.onResponse(responseContext);

                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    return false;
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all requests to complete
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert - all requests completed
        assertThat(completed).isTrue();
        assertThat(errorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(totalRequests);

        // Assert - all futures completed successfully
        for (Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }

        // Assert - correct number of spans generated
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(totalRequests);

        // Assert - all spans are independent with unique attributes
        Set<String> uniqueModels = spans.stream()
                .map(span -> span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .collect(Collectors.toSet());
        assertThat(uniqueModels).hasSize(totalRequests);

        // Assert - all spans have correct naming and status
        for (SpanData span : spans) {
            assertThat(span.getName()).isEqualTo(GenAiSpanNames.CHAT);
            assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

            // Verify span has valid timing
            assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
        }

        // Assert - no duplicate span IDs (each request created a unique span)
        Set<String> uniqueSpanIds = spans.stream()
                .map(SpanData::getSpanId)
                .collect(Collectors.toSet());
        assertThat(uniqueSpanIds).hasSize(totalRequests);

        // Assert - token usage values are correct (no cross-contamination)
        Map<String, Long> modelToInputTokens = new HashMap<>();
        for (SpanData span : spans) {
            String model = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
            Long inputTokens = span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS);

            // Extract request ID from model name and verify expected token count
            int expectedRequestId = Integer.parseInt(model.replace("model-", ""));
            int expectedInputTokens = 100 + expectedRequestId;
            assertThat(inputTokens).isEqualTo((long) expectedInputTokens);

            modelToInputTokens.put(model, inputTokens);
        }
        assertThat(modelToInputTokens).hasSize(totalRequests);
    }

    /**
     * Test Case 2: Concurrent streaming requests.
     * Expected: Each stream maintains its own context throughout.
     */
    @Test
    void concurrentStreamingRequests_maintainsOwnContext() throws Exception {
        // Arrange
        final int streamCount = 20;
        final int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(streamCount);

        List<Future<StreamResult>> futures = new ArrayList<>();
        AtomicInteger completedStreams = new AtomicInteger(0);

        // Act - start all streaming requests concurrently
        for (int i = 0; i < streamCount; i++) {
            final int streamId = i;
            final String uniqueModel = "stream-model-" + streamId;
            final int chunkCount = 5 + (streamId % 5); // 5-9 chunks per stream

            Future<StreamResult> future = executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);

                    ConcurrentStreamingChatModel chatModel = new ConcurrentStreamingChatModel(
                            List.of(streamingListener),
                            chunkCount,
                            streamId * 10 + 50, // unique input tokens
                            streamId * 5 + 25,  // unique output tokens
                            uniqueModel
                    );

                    ChatRequest request = ChatRequest.builder()
                            .messages(UserMessage.from("Stream request " + streamId))
                            .modelName(uniqueModel)
                            .build();

                    CountDownLatch streamLatch = new CountDownLatch(1);
                    AtomicInteger receivedChunks = new AtomicInteger(0);
                    List<String> partialResponses = Collections.synchronizedList(new ArrayList<>());

                    chatModel.chat(request, new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            receivedChunks.incrementAndGet();
                            partialResponses.add(partialResponse);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            completedStreams.incrementAndGet();
                            streamLatch.countDown();
                        }

                        @Override
                        public void onError(Throwable error) {
                            streamLatch.countDown();
                        }
                    });

                    streamLatch.await(10, TimeUnit.SECONDS);

                    return new StreamResult(
                            streamId,
                            uniqueModel,
                            receivedChunks.get(),
                            chunkCount,
                            partialResponses
                    );
                } catch (Exception e) {
                    return new StreamResult(streamId, uniqueModel, -1, chunkCount, List.of());
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Release all threads
        startLatch.countDown();

        // Wait for all streams to complete
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        assertThat(completed).isTrue();
        assertThat(completedStreams.get()).isEqualTo(streamCount);

        // Verify each stream result
        for (Future<StreamResult> future : futures) {
            StreamResult result = future.get();
            assertThat(result.receivedChunks).isEqualTo(result.expectedChunks);
            // Verify chunks belong to this stream (contain stream ID)
            for (String chunk : result.partialResponses) {
                assertThat(chunk).contains(String.valueOf(result.streamId));
            }
        }

        // Verify all spans were created with correct attributes
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(streamCount);

        // Verify each span has unique model name (no context leakage)
        Set<String> uniqueModels = spans.stream()
                .map(span -> span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                .collect(Collectors.toSet());
        assertThat(uniqueModels).hasSize(streamCount);

        // Verify streaming flag is set on all spans
        for (SpanData span : spans) {
            assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING)).isTrue();
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        }
    }

    /**
     * Test Case 3: Mixed sync and async requests.
     * Expected: No deadlocks or context corruption.
     */
    @Test
    void mixedSyncAndAsyncRequests_noDeadlocksOrCorruption() throws Exception {
        // Arrange
        final int totalRequests = 50;
        final int syncRequests = 25;
        final int asyncRequests = 25;
        final int threadPoolSize = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(totalRequests);

        AtomicInteger syncSuccess = new AtomicInteger(0);
        AtomicInteger asyncSuccess = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        // Submit mixed sync and async requests
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            final boolean isAsync = requestId >= syncRequests;
            final String requestType = isAsync ? "async" : "sync";
            final String uniqueModel = requestType + "-model-" + requestId;

            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);

                    if (isAsync) {
                        // Streaming request
                        executeStreamingRequest(requestId, uniqueModel, asyncSuccess, completionLatch);
                    } else {
                        // Sync request
                        executeSyncRequest(requestId, uniqueModel, syncSuccess, completionLatch);
                    }
                } catch (Exception e) {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Release all threads
        startLatch.countDown();

        // Wait with timeout to detect deadlocks
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - no deadlock (completed within timeout)
        assertThat(completed)
                .withFailMessage("Deadlock detected - requests did not complete within timeout")
                .isTrue();
        assertThat(terminated)
                .withFailMessage("Executor did not terminate - possible thread leak")
                .isTrue();

        // Assert - all requests succeeded
        assertThat(syncSuccess.get()).isEqualTo(syncRequests);
        assertThat(asyncSuccess.get()).isEqualTo(asyncRequests);

        // Assert - correct number of spans
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(totalRequests);

        // Separate and verify sync vs streaming spans
        List<SpanData> syncSpans = spans.stream()
                .filter(span -> {
                    Boolean streaming = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING);
                    return streaming == null || !streaming;
                })
                .collect(Collectors.toList());

        List<SpanData> streamingSpans = spans.stream()
                .filter(span -> {
                    Boolean streaming = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING);
                    return streaming != null && streaming;
                })
                .collect(Collectors.toList());

        assertThat(syncSpans).hasSize(syncRequests);
        assertThat(streamingSpans).hasSize(asyncRequests);

        // Verify no attribute corruption - each span should have matching model name
        for (SpanData span : spans) {
            String model = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL);
            assertThat(model).isNotNull();

            Boolean isStreaming = span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_STREAMING);
            if (Boolean.TRUE.equals(isStreaming)) {
                assertThat(model).startsWith("async-model-");
            } else {
                assertThat(model).startsWith("sync-model-");
            }
        }

        // Verify all spans have valid status
        for (SpanData span : spans) {
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
            assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
        }

        // Verify no duplicate span IDs
        Set<String> uniqueSpanIds = spans.stream()
                .map(SpanData::getSpanId)
                .collect(Collectors.toSet());
        assertThat(uniqueSpanIds).hasSize(totalRequests);
    }

    private void executeSyncRequest(int requestId, String modelName,
                                    AtomicInteger successCounter, CountDownLatch latch) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Sync request " + requestId))
                    .modelName(modelName)
                    .build();

            Map<Object, Object> attributes = new ConcurrentHashMap<>();

            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request, ModelProvider.OPEN_AI, attributes);

            syncListener.onRequest(requestContext);

            // Simulate processing
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 15));

            ChatResponse response = ChatResponse.builder()
                    .aiMessage(AiMessage.from("Sync response " + requestId))
                    .id("sync-response-" + requestId)
                    .tokenUsage(new TokenUsage(50 + requestId, 30 + requestId))
                    .finishReason(FinishReason.STOP)
                    .build();

            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    response, request, ModelProvider.OPEN_AI, attributes);

            syncListener.onResponse(responseContext);
            successCounter.incrementAndGet();
        } catch (Exception e) {
            // Log but don't fail - let the test assertions catch issues
        } finally {
            latch.countDown();
        }
    }

    private void executeStreamingRequest(int requestId, String modelName,
                                         AtomicInteger successCounter, CountDownLatch latch) {
        try {
            ConcurrentStreamingChatModel chatModel = new ConcurrentStreamingChatModel(
                    List.of(streamingListener),
                    3, // chunks
                    50 + requestId,
                    30 + requestId,
                    modelName
            );

            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Streaming request " + requestId))
                    .modelName(modelName)
                    .build();

            CountDownLatch streamLatch = new CountDownLatch(1);

            chatModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // Process partial response
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    successCounter.incrementAndGet();
                    streamLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    streamLatch.countDown();
                }
            });

            streamLatch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Log but don't fail
        } finally {
            latch.countDown();
        }
    }

    /**
     * Result holder for streaming operations.
     */
    private static class StreamResult {
        final int streamId;
        final String modelName;
        final int receivedChunks;
        final int expectedChunks;
        final List<String> partialResponses;

        StreamResult(int streamId, String modelName, int receivedChunks,
                     int expectedChunks, List<String> partialResponses) {
            this.streamId = streamId;
            this.modelName = modelName;
            this.receivedChunks = receivedChunks;
            this.expectedChunks = expectedChunks;
            this.partialResponses = partialResponses;
        }
    }

    /**
     * Test streaming chat model with configurable behavior for concurrency testing.
     */
    private static class ConcurrentStreamingChatModel implements StreamingChatModel {
        private final List<ChatModelListener> listeners;
        private final int numberOfChunks;
        private final int inputTokens;
        private final int outputTokens;
        private final String modelName;

        ConcurrentStreamingChatModel(List<ChatModelListener> listeners, int numberOfChunks,
                                     int inputTokens, int outputTokens, String modelName) {
            this.listeners = listeners;
            this.numberOfChunks = numberOfChunks;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.modelName = modelName;
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
            // Extract stream ID from model name for unique responses
            String streamIdStr = modelName.replaceAll(".*-", "");

            // Run async to simulate real streaming behavior
            CompletableFuture.runAsync(() -> {
                try {
                    // Emit chunks with slight delays
                    for (int i = 0; i < numberOfChunks; i++) {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(2, 8));
                        // Include stream ID in chunk for verification
                        handler.onPartialResponse("chunk-" + streamIdStr + "-" + i + " ");
                    }

                    // Complete successfully
                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Complete stream " + streamIdStr))
                            .id("stream-response-" + streamIdStr)
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
