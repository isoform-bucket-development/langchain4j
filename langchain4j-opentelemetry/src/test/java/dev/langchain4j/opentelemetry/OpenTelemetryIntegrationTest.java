package dev.langchain4j.opentelemetry;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for OpenTelemetry end-to-end functionality.
 * These tests are designed to fail initially (TDD red phase) and validate:
 * 1. Full tracing workflow from AiService to ChatModel
 * 2. Proper span hierarchy and context propagation
 * 3. Metrics collection across the full request lifecycle
 * 4. Performance characteristics meet requirements
 */
class OpenTelemetryIntegrationTest {

    private OpenTelemetry openTelemetry;
    private InMemorySpanExporter spanExporter;
    private InMemoryMetricReader metricReader;
    private TestAiService aiService;

    // Test AiService interface for integration testing
    interface TestAiService {
        String generateResponse(String prompt);
    }

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        metricReader = InMemoryMetricReader.create();

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build())
            .setMeterProvider(SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build())
            .build();

        // This will fail because OpenTelemetry integration doesn't exist yet
        try {
            // Create a test ChatLanguageModel that doesn't actually call external services
            ChatLanguageModel mockChatModel = createMockChatLanguageModel();

            // This should auto-register OpenTelemetry listeners but will fail
            aiService = AiServices.builder(TestAiService.class)
                .chatLanguageModel(mockChatModel)
                .build();
        } catch (Exception e) {
            fail("OpenTelemetry integration not yet implemented: " + e.getMessage());
        }
    }

    @Test
    void shouldCreateEndToEndTraceForAiServiceInvocation() {
        // This test will fail because the full integration doesn't exist
        String prompt = "What is the capital of France?";
        String response = aiService.generateResponse(prompt);

        // Should create hierarchical spans: AiService -> ChatModel
        assertEquals(2, spanExporter.getFinishedSpanItems().size(),
            "Should create both AiService and ChatModel spans");

        var spans = spanExporter.getFinishedSpanItems();

        // Find root span (AiService)
        var rootSpan = spans.stream()
            .filter(span -> span.getParentSpanId().equals("0000000000000000"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Should have a root span"));

        // Find child span (ChatModel)
        var childSpan = spans.stream()
            .filter(span -> !span.getParentSpanId().equals("0000000000000000"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Should have a child span"));

        // Verify span hierarchy
        assertEquals("aiservice.invoke", rootSpan.getName(),
            "Root span should be AiService invocation");
        assertEquals(SpanKind.SERVER, rootSpan.getKind(),
            "Root span should be SERVER kind");

        assertEquals("chat_model.generate", childSpan.getName(),
            "Child span should be ChatModel generation");
        assertEquals(SpanKind.CLIENT, childSpan.getKind(),
            "Child span should be CLIENT kind");

        assertEquals(rootSpan.getSpanId(), childSpan.getParentSpanId(),
            "Child span should be parented to root span");
    }

    @Test
    void shouldCollectMetricsForFullRequestLifecycle() {
        // This test will fail because metrics integration doesn't exist
        String prompt = "Test prompt";
        String response = aiService.generateResponse(prompt);

        var metrics = metricReader.collectAllMetrics();

        // Should have collected request count metrics
        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.aiservice.requests")),
            "Should collect AiService request metrics");

        // Should have collected latency metrics
        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.aiservice.duration")),
            "Should collect AiService latency metrics");

        // Should have collected token usage metrics
        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.tokens.input")),
            "Should collect input token metrics");

        assertTrue(metrics.stream()
                .anyMatch(metric -> metric.getName().equals("langchain4j.tokens.output")),
            "Should collect output token metrics");
    }

    @Test
    void shouldPropagateContextThroughToolExecution() {
        // This test will fail because tool execution tracing doesn't exist
        TestAiServiceWithTools aiServiceWithTools = createAiServiceWithTools();

        String response = aiServiceWithTools.processWithTool("Calculate 2+2");

        var spans = spanExporter.getFinishedSpanItems();

        // Should have spans for: AiService -> ChatModel -> Tool execution
        assertTrue(spans.size() >= 3,
            "Should have spans for AiService, ChatModel, and tool execution");

        // All spans should be part of the same trace
        String traceId = spans.get(0).getTraceId();
        assertTrue(spans.stream().allMatch(span -> span.getTraceId().equals(traceId)),
            "All spans should be part of the same trace");

        // Find tool execution span
        var toolSpan = spans.stream()
            .filter(span -> span.getName().contains("tool"))
            .findFirst();

        assertTrue(toolSpan.isPresent(), "Should have tool execution span");
    }

    @Test
    void shouldRespectPrivacySettingsByDefault() {
        // This test will fail because privacy controls don't exist
        String sensitivePrompt = "My SSN is 123-45-6789 and my password is secret123";
        String response = aiService.generateResponse(sensitivePrompt);

        var spans = spanExporter.getFinishedSpanItems();
        var chatModelSpan = spans.stream()
            .filter(span -> span.getName().equals("chat_model.generate"))
            .findFirst()
            .orElseThrow();

        // Content should be redacted by default
        String prompt = chatModelSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt"));
        assertTrue(prompt == null || prompt.equals("[REDACTED]") || !prompt.contains("123-45-6789"),
            "Sensitive content should be redacted by default");
    }

    @Test
    void shouldMaintainPerformanceRequirements() {
        // This test will fail because performance optimization doesn't exist
        int iterations = 100;
        long baselineStart = System.currentTimeMillis();

        // Measure baseline performance without telemetry
        for (int i = 0; i < iterations; i++) {
            // Simulate AI operation without telemetry
            simulateAiOperationWithoutTelemetry();
        }

        long baselineDuration = System.currentTimeMillis() - baselineStart;

        // Clear previous telemetry data
        spanExporter.reset();

        long telemetryStart = System.currentTimeMillis();

        // Measure performance with telemetry
        for (int i = 0; i < iterations; i++) {
            aiService.generateResponse("Test prompt " + i);
        }

        long telemetryDuration = System.currentTimeMillis() - telemetryStart;

        // Calculate overhead percentage
        double overheadPercent = ((double) (telemetryDuration - baselineDuration) / baselineDuration) * 100;

        assertTrue(overheadPercent < 5.0,
            "Telemetry overhead should be <5%, actual overhead: " + overheadPercent + "%");
    }

    @Test
    void shouldHandleHighVolumeRequestsWithSampling() {
        // This test will fail because sampling configuration doesn't exist
        try {
            // Configure 10% sampling rate
            configureSampling(0.1);

            int totalRequests = 1000;
            for (int i = 0; i < totalRequests; i++) {
                aiService.generateResponse("Request " + i);
            }

            var spans = spanExporter.getFinishedSpanItems();

            // Should have approximately 10% of requests traced (allow some variance)
            int expectedSpans = (int) (totalRequests * 0.1);
            int tolerance = expectedSpans / 2; // 50% tolerance

            assertTrue(spans.size() >= expectedSpans - tolerance &&
                      spans.size() <= expectedSpans + tolerance,
                "Should sample approximately 10% of requests, got " + spans.size() + " spans out of " + totalRequests + " requests");

        } catch (Exception e) {
            fail("Sampling configuration not yet implemented: " + e.getMessage());
        }
    }

    @Test
    void shouldGracefullyHandleOpenTelemetryFailures() {
        // This test will fail because error handling doesn't exist
        try {
            // Simulate OpenTelemetry export failure
            simulateExportFailure();

            // AI service should continue to work despite telemetry failures
            String response = aiService.generateResponse("Test prompt");

            assertNotNull(response, "AiService should continue working despite telemetry failures");

        } catch (Exception e) {
            fail("Graceful degradation not yet implemented: " + e.getMessage());
        }
    }

    // Helper methods that will fail because implementations don't exist

    private ChatLanguageModel createMockChatLanguageModel() {
        // This will fail because we need to create a mock that integrates with telemetry
        return new ChatLanguageModel() {
            @Override
            public String generate(String message) {
                // Simulate some processing time
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Mock response to: " + message;
            }
        };
    }

    interface TestAiServiceWithTools {
        String processWithTool(String input);
    }

    private TestAiServiceWithTools createAiServiceWithTools() {
        fail("Tool integration testing not yet implemented");
        return null;
    }

    private void configureSampling(double samplingRate) {
        fail("Sampling configuration not yet implemented");
    }

    private void simulateAiOperationWithoutTelemetry() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateExportFailure() {
        fail("Export failure simulation not yet implemented");
    }
}