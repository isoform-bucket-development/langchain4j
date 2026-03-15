package dev.langchain4j.opentelemetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import dev.langchain4j.opentelemetry.metrics.GenAiMetrics;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenTelemetryLangChain4j plain Java manual configuration.
 * <p>
 * Scenario 17: Verify listener can be manually configured in plain Java without frameworks
 * </p>
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>Test Case 1: Builder with default configuration - Listener created with tracing, metrics, and content capture enabled</li>
 *   <li>Test Case 2: Builder with custom tracer provider - Listener uses provided TracerProvider instead of global</li>
 *   <li>Test Case 3: Builder with custom meter provider - Listener uses provided MeterProvider for metrics</li>
 *   <li>Test Case 4: Integration with multiple ChatModel instances - Same listener can be registered with multiple models</li>
 * </ul>
 */
class OpenTelemetryLangChain4jTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    /**
     * Creates a simple chat request context for testing.
     */
    private ChatModelRequestContext createRequestContext(Map<Object, Object> attributes, String modelName) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello, test!"))
                .modelName(modelName)
                .build();

        return new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attributes);
    }

    /**
     * Creates a simple chat response context for testing.
     */
    private ChatModelResponseContext createResponseContext(ChatModelRequestContext requestContext,
                                                           Map<Object, Object> attributes) {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello! Response here."))
                .id("test-response-id")
                .tokenUsage(new TokenUsage(10, 20))
                .finishReason(FinishReason.STOP)
                .build();

        return new ChatModelResponseContext(
                response,
                requestContext.chatRequest(),
                ModelProvider.OPEN_AI,
                attributes
        );
    }

    /**
     * Test Case 1: Builder with default configuration
     * Expected: Listener created with tracing, metrics, and content capture enabled
     *
     * This test verifies that the default builder creates a fully-functional
     * OpenTelemetryLangChain4j instance with all features enabled.
     */
    @Test
    void builderWithDefaultConfiguration_createsListenerWithAllFeaturesEnabled() {
        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder().build();

        // Assert - Verify default configuration
        assertThat(otel.isTracingEnabled())
                .as("Tracing should be enabled by default")
                .isTrue();

        assertThat(otel.isMetricsEnabled())
                .as("Metrics should be enabled by default")
                .isTrue();

        assertThat(otel.getContentCaptureMode())
                .as("Content capture should be FULL by default")
                .isEqualTo(ContentCaptureMode.FULL);

        // Verify we can create a listener
        OpenTelemetryChatModelListener listener = otel.createChatModelListener();
        assertThat(listener)
                .as("Should be able to create a ChatModelListener")
                .isNotNull();

        // Verify listener has default config
        assertThat(listener.getConfig().isTracingEnabled()).isTrue();
        assertThat(listener.getConfig().isMetricsEnabled()).isTrue();
        assertThat(listener.getConfig().getContentCaptureMode()).isEqualTo(ContentCaptureMode.FULL);
    }

    /**
     * Test Case 1 (Additional): Verify default builder creates working streaming listener
     */
    @Test
    void builderWithDefaultConfiguration_createsWorkingStreamingListener() {
        // Arrange
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .build();

        // Act
        OpenTelemetryChatModelListener streamingListener = otel.createStreamingChatModelListener();

        // Assert
        assertThat(streamingListener)
                .as("Should be able to create a streaming ChatModelListener")
                .isNotNull();

        assertThat(streamingListener.isStreaming())
                .as("Streaming listener should have streaming flag set to true")
                .isTrue();
    }

    /**
     * Test Case 2: Builder with custom tracer provider
     * Expected: Listener uses provided TracerProvider instead of global
     *
     * This test verifies that when a custom TracerProvider is provided via
     * the builder, the created listener uses it for span creation.
     */
    @Test
    void builderWithCustomTracerProvider_listenerUsesProvidedProvider() {
        // Arrange
        TracerProvider customTracerProvider = otelTesting.getOpenTelemetry().getTracerProvider();

        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(customTracerProvider)
                .build();

        OpenTelemetryChatModelListener listener = otel.createChatModelListener();

        // Assert - Verify the listener is using the custom provider
        assertThat(otel.getTracerProvider())
                .as("Should use the provided TracerProvider")
                .isSameAs(customTracerProvider);

        // Verify the listener actually creates spans using the custom provider
        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = createRequestContext(attributes, "gpt-4");
        ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Verify span was created in the custom provider's scope
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("Span should be created using the custom TracerProvider")
                .hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName())
                .as("Span should have correct name")
                .isEqualTo("gen_ai.chat");
    }

    /**
     * Test Case 2 (Additional): Verify custom tracer provider with different SDK instance
     */
    @Test
    void builderWithCustomTracerProvider_spansAreIsolatedToProvider() {
        // Arrange - Create a separate SDK for isolation testing
        SdkTracerProvider customSdkProvider = SdkTracerProvider.builder().build();

        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(customSdkProvider)
                .build();

        // Assert
        assertThat(otel.getTracerProvider())
                .as("Should use the custom SdkTracerProvider")
                .isSameAs(customSdkProvider);

        // Cleanup
        customSdkProvider.close();
    }

    /**
     * Test Case 3: Builder with custom meter provider
     * Expected: Listener uses provided MeterProvider for metrics
     *
     * This test verifies that when a custom MeterProvider is provided via
     * the builder, the created metrics instruments use it.
     */
    @Test
    void builderWithCustomMeterProvider_listenerUsesProvidedProviderForMetrics() {
        // Arrange - Create custom MeterProvider with InMemoryMetricReader
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider customMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .meterProvider(customMeterProvider)
                .build();

        // Assert - Verify the custom provider is used
        assertThat(otel.getMeterProvider())
                .as("Should use the provided MeterProvider")
                .isSameAs(customMeterProvider);

        // Verify metrics instance was created
        GenAiMetrics metrics = otel.getMetrics();
        assertThat(metrics)
                .as("Metrics should be created with the custom MeterProvider")
                .isNotNull();

        // Record a metric to verify it uses the custom provider
        metrics.getTokenUsageCounter().add(100);

        // Verify metric was recorded in the custom provider
        Collection<MetricData> metricData = metricReader.collectAllMetrics();
        assertThat(metricData)
                .as("Metrics should be recorded in the custom MeterProvider")
                .isNotEmpty();

        // Cleanup
        customMeterProvider.close();
    }

    /**
     * Test Case 3 (Additional): Verify metrics are recorded with correct attributes
     */
    @Test
    void builderWithCustomMeterProvider_metricsRecordedWithAttributes() {
        // Arrange
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider customMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .meterProvider(customMeterProvider)
                .build();

        GenAiMetrics metrics = otel.getMetrics();

        // Act - Record multiple types of metrics
        metrics.getInputTokensCounter().add(50);
        metrics.getOutputTokensCounter().add(100);
        metrics.getOperationDurationHistogram().record(1.5);

        // Assert
        Collection<MetricData> metricData = metricReader.collectAllMetrics();
        assertThat(metricData)
                .as("Multiple metrics should be recorded")
                .hasSizeGreaterThanOrEqualTo(3);

        // Cleanup
        customMeterProvider.close();
    }

    /**
     * Test Case 4: Integration with multiple ChatModel instances
     * Expected: Same listener can be registered with multiple models
     *
     * This test verifies that a single OpenTelemetryLangChain4j instance
     * can create listeners that work correctly when registered with
     * multiple ChatModel instances simultaneously.
     */
    @Test
    void integrationWithMultipleChatModels_sameListenerWorksWithMultipleModels() {
        // Arrange
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .build();

        OpenTelemetryChatModelListener listener = otel.createChatModelListener();

        // Act - Simulate requests from multiple models using the same listener
        // Model 1: GPT-4
        Map<Object, Object> attributes1 = new HashMap<>();
        ChatModelRequestContext requestContext1 = createRequestContext(attributes1, "gpt-4");
        ChatModelResponseContext responseContext1 = createResponseContext(requestContext1, attributes1);

        listener.onRequest(requestContext1);
        listener.onResponse(responseContext1);

        // Model 2: Claude
        Map<Object, Object> attributes2 = new HashMap<>();
        ChatRequest request2 = ChatRequest.builder()
                .messages(UserMessage.from("Hello from Claude!"))
                .modelName("claude-3-sonnet")
                .build();
        ChatModelRequestContext requestContext2 = new ChatModelRequestContext(
                request2, ModelProvider.ANTHROPIC, attributes2);

        ChatResponse response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response from Claude"))
                .id("claude-response-id")
                .tokenUsage(new TokenUsage(15, 25))
                .finishReason(FinishReason.STOP)
                .build();
        ChatModelResponseContext responseContext2 = new ChatModelResponseContext(
                response2, request2, ModelProvider.ANTHROPIC, attributes2);

        listener.onRequest(requestContext2);
        listener.onResponse(responseContext2);

        // Model 3: Ollama (simulated)
        Map<Object, Object> attributes3 = new HashMap<>();
        ChatRequest request3 = ChatRequest.builder()
                .messages(UserMessage.from("Hello from Ollama!"))
                .modelName("llama2")
                .build();
        ChatModelRequestContext requestContext3 = new ChatModelRequestContext(
                request3, ModelProvider.OLLAMA, attributes3);

        ChatResponse response3 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Response from Ollama"))
                .id("ollama-response-id")
                .tokenUsage(new TokenUsage(8, 12))
                .finishReason(FinishReason.STOP)
                .build();
        ChatModelResponseContext responseContext3 = new ChatModelResponseContext(
                response3, request3, ModelProvider.OLLAMA, attributes3);

        listener.onRequest(requestContext3);
        listener.onResponse(responseContext3);

        // Assert - Verify spans were created for all three model interactions
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("Spans should be created for all three model interactions")
                .hasSize(3);

        // Verify each span has the correct provider attribute
        assertThat(spans)
                .extracting(span -> span.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.system")))
                .containsExactlyInAnyOrder("open_ai", "anthropic", "ollama");

        // Verify each span has the correct model name
        assertThat(spans)
                .extracting(span -> span.getAttributes().get(
                        io.opentelemetry.api.common.AttributeKey.stringKey("gen_ai.request.model")))
                .containsExactlyInAnyOrder("gpt-4", "claude-3-sonnet", "llama2");
    }

    /**
     * Test Case 4 (Additional): Multiple listeners from same builder work independently
     */
    @Test
    void multipleListenersFromSameBuilder_workIndependently() {
        // Arrange
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .build();

        // Create multiple listeners
        OpenTelemetryChatModelListener listener1 = otel.createChatModelListener();
        OpenTelemetryChatModelListener listener2 = otel.createChatModelListener();
        OpenTelemetryChatModelListener streamingListener = otel.createStreamingChatModelListener();

        // Assert - Each listener is a separate instance
        assertThat(listener1)
                .as("First listener should not be same instance as second")
                .isNotSameAs(listener2);

        assertThat(streamingListener)
                .as("Streaming listener should not be same as regular listener")
                .isNotSameAs(listener1);

        // Verify they all share the same configuration
        assertThat(listener1.getConfig().isTracingEnabled())
                .isEqualTo(listener2.getConfig().isTracingEnabled())
                .isEqualTo(streamingListener.getConfig().isTracingEnabled());

        // Verify streaming listener has streaming flag
        assertThat(listener1.isStreaming()).isFalse();
        assertThat(listener2.isStreaming()).isFalse();
        assertThat(streamingListener.isStreaming()).isTrue();
    }

    /**
     * Test: Builder configuration methods work fluently
     */
    @Test
    void builderConfigurationMethods_workFluently() {
        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .tracingEnabled(true)
                .metricsEnabled(true)
                .contentCaptureMode(ContentCaptureMode.METADATA)
                .samplingRate(0.5)
                .build();

        // Assert
        assertThat(otel.isTracingEnabled()).isTrue();
        assertThat(otel.isMetricsEnabled()).isTrue();
        assertThat(otel.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);
        assertThat(otel.getConfig().getSamplingRate()).isEqualTo(0.5);
    }

    /**
     * Test: Builder with config object
     */
    @Test
    void builderWithConfigObject_appliesAllSettings() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .metricsEnabled(true)
                .contentCaptureMode(ContentCaptureMode.NONE)
                .samplingRate(0.25)
                .build();

        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .config(config)
                .build();

        // Assert
        assertThat(otel.isTracingEnabled()).isFalse();
        assertThat(otel.isMetricsEnabled()).isTrue();
        assertThat(otel.getContentCaptureMode()).isEqualTo(ContentCaptureMode.NONE);
        assertThat(otel.getConfig().getSamplingRate()).isEqualTo(0.25);
    }

    /**
     * Test: Builder null handling - defaults to global providers
     */
    @Test
    void builderWithNullProviders_usesDefaults() {
        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(null)
                .meterProvider(null)
                .config(null)
                .build();

        // Assert - Should use global providers (no exceptions)
        assertThat(otel.getTracerProvider()).isNotNull();
        assertThat(otel.getMeterProvider()).isNotNull();
        assertThat(otel.getConfig()).isNotNull();
        assertThat(otel.getConfig().isTracingEnabled()).isTrue(); // default value
    }

    /**
     * Test: Verify getters return correct values
     */
    @Test
    void getters_returnCorrectValues() {
        // Arrange
        TracerProvider tracerProvider = otelTesting.getOpenTelemetry().getTracerProvider();
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .metricsEnabled(false)
                .contentCaptureMode(ContentCaptureMode.METADATA)
                .build();

        // Act
        OpenTelemetryLangChain4j otel = OpenTelemetryLangChain4j.builder()
                .tracerProvider(tracerProvider)
                .meterProvider(meterProvider)
                .config(config)
                .build();

        // Assert
        assertThat(otel.getTracerProvider()).isSameAs(tracerProvider);
        assertThat(otel.getMeterProvider()).isSameAs(meterProvider);
        assertThat(otel.getConfig()).isSameAs(config);
        assertThat(otel.getMetrics()).isNotNull();
        assertThat(otel.isTracingEnabled()).isTrue();
        assertThat(otel.isMetricsEnabled()).isFalse();
        assertThat(otel.getContentCaptureMode()).isEqualTo(ContentCaptureMode.METADATA);

        // Cleanup
        meterProvider.close();
    }
}
