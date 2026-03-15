package dev.langchain4j.opentelemetry.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.opentelemetry.metrics.GenAiMetrics;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for independent enable/disable configuration of metrics.
 * <p>
 * This test class verifies that metrics can be enabled or disabled independently
 * of tracing via {@link OpenTelemetryLangChain4jConfig}.
 * </p>
 *
 * <p>Test cases:</p>
 * <ul>
 *   <li>Test Case 1: metrics=false, tracing=true → Spans created but no metrics emitted</li>
 *   <li>Test Case 2: metrics=true, tracing=false → Metrics emitted but no spans created</li>
 * </ul>
 */
class MetricsConfigurationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private InMemoryMetricReader metricReader;
    private SdkMeterProvider meterProvider;

    @BeforeEach
    void setUp() {
        metricReader = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
    }

    /**
     * Test Case 1: Configuration with metrics=false, tracing=true
     * Expected: Spans created but no metrics emitted
     */
    @Test
    @DisplayName("Test Case 1: metrics=false, tracing=true - spans created but no metrics emitted")
    void metricsDisabledTracingEnabled_spansCreatedNoMetrics() {
        // Arrange: Configure with metrics disabled, tracing enabled
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .metricsEnabled(false)
                .tracingEnabled(true)
                .build();

        ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
                .config(config)
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .meterProvider(meterProvider)
                .build();

        // Verify config is correct
        assertThat(listener.isMetricsEnabled()).isFalse();
        assertThat(listener.isTracingEnabled()).isTrue();

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

        // Assert: Spans should be created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo(GenAiSpanNames.CHAT);
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Assert: No metrics should be emitted
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isEmpty();
    }

    /**
     * Test Case 2: Configuration with metrics=true, tracing=false
     * Expected: Metrics emitted but no spans created
     */
    @Test
    @DisplayName("Test Case 2: metrics=true, tracing=false - metrics emitted but no spans created")
    void metricsEnabledTracingDisabled_metricsEmittedNoSpans() {
        // Arrange: Configure with metrics enabled, tracing disabled
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .metricsEnabled(true)
                .tracingEnabled(false)
                .build();

        ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
                .config(config)
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .meterProvider(meterProvider)
                .build();

        // Verify config is correct
        assertThat(listener.isMetricsEnabled()).isTrue();
        assertThat(listener.isTracingEnabled()).isFalse();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of France?"))
                .modelName("gpt-4o-mini")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("The capital of France is Paris."))
                .id("response-002")
                .tokenUsage(new TokenUsage(20, 30))
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

        // Assert: No spans should be created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).isEmpty();

        // Assert: Metrics should be emitted
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotEmpty();

        // Verify token usage counter
        Optional<MetricData> tokenUsageMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();

        assertThat(tokenUsageMetric).isPresent();

        List<LongPointData> points = tokenUsageMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());
        assertThat(points).hasSize(1);

        // Total tokens should be 50 (20 input + 30 output)
        assertThat(points.get(0).getValue()).isEqualTo(50);

        // Verify attributes
        assertThat(points.get(0).getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM))
                .isEqualTo("open_ai");
        assertThat(points.get(0).getAttributes().get(GenAiMetrics.ATTR_GEN_AI_REQUEST_MODEL))
                .isEqualTo("gpt-4o-mini");
        assertThat(points.get(0).getAttributes().get(GenAiMetrics.ATTR_GEN_AI_OPERATION_NAME))
                .isEqualTo("chat");
    }

    /**
     * Additional test: Both metrics and tracing enabled (default configuration)
     */
    @Test
    @DisplayName("Both metrics and tracing enabled - both spans and metrics emitted")
    void bothEnabled_spansAndMetricsEmitted() {
        // Arrange: Default configuration (both enabled)
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.defaultConfig();

        ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
                .config(config)
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .meterProvider(meterProvider)
                .build();

        // Verify defaults
        assertThat(listener.isMetricsEnabled()).isTrue();
        assertThat(listener.isTracingEnabled()).isTrue();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Generate a haiku"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Cherry blossoms fall..."))
                .id("response-003")
                .tokenUsage(new TokenUsage(5, 10))
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

        // Assert: Spans should be created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).getName()).isEqualTo(GenAiSpanNames.CHAT);

        // Assert: Metrics should be emitted
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isNotEmpty();

        Optional<MetricData> tokenUsageMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();
        assertThat(tokenUsageMetric).isPresent();
    }

    /**
     * Additional test: Both metrics and tracing disabled
     */
    @Test
    @DisplayName("Both metrics and tracing disabled - neither spans nor metrics emitted")
    void bothDisabled_noSpansNoMetrics() {
        // Arrange: Both disabled
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .metricsEnabled(false)
                .tracingEnabled(false)
                .build();

        ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
                .config(config)
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .meterProvider(meterProvider)
                .build();

        assertThat(listener.isMetricsEnabled()).isFalse();
        assertThat(listener.isTracingEnabled()).isFalse();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Silent request"))
                .modelName("gpt-4")
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                ModelProvider.OPEN_AI,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Silent response"))
                .id("response-004")
                .tokenUsage(new TokenUsage(5, 5))
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

        // Assert: No spans should be created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans).isEmpty();

        // Assert: No metrics should be emitted
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        assertThat(metrics).isEmpty();
    }

    /**
     * Test configuration builder maintains independence of settings
     */
    @Test
    @DisplayName("Configuration builder maintains independence of metrics and tracing settings")
    void configurationBuilderMaintainsIndependence() {
        // Test that setting one doesn't affect the other
        OpenTelemetryLangChain4jConfig config1 = OpenTelemetryLangChain4jConfig.builder()
                .metricsEnabled(false)
                .build();

        assertThat(config1.isMetricsEnabled()).isFalse();
        assertThat(config1.isTracingEnabled()).isTrue(); // Default should be true

        OpenTelemetryLangChain4jConfig config2 = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        assertThat(config2.isTracingEnabled()).isFalse();
        assertThat(config2.isMetricsEnabled()).isTrue(); // Default should be true
    }

    /**
     * Test metrics with different providers
     */
    @Test
    @DisplayName("Metrics enabled with tracing disabled - different providers")
    void metricsEnabledDifferentProviders_correctAttributesRecorded() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .metricsEnabled(true)
                .tracingEnabled(false)
                .build();

        ConfiguredChatModelListener listener = ConfiguredChatModelListener.builder()
                .config(config)
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .meterProvider(meterProvider)
                .build();

        // Request 1: OpenAI
        executeRequest(listener, "gpt-4", ModelProvider.OPEN_AI, 10, 20);

        // Request 2: Anthropic
        executeRequest(listener, "claude-3-sonnet", ModelProvider.ANTHROPIC, 15, 25);

        // Assert: No spans
        assertThat(otelTesting.getSpans()).isEmpty();

        // Assert: Metrics from both providers
        Collection<MetricData> metrics = metricReader.collectAllMetrics();
        Optional<MetricData> tokenUsageMetric = metrics.stream()
                .filter(m -> m.getName().equals(GenAiMetrics.TOKEN_USAGE_COUNTER_NAME))
                .findFirst();

        assertThat(tokenUsageMetric).isPresent();

        List<LongPointData> points = tokenUsageMetric.get().getLongSumData().getPoints()
                .stream().collect(Collectors.toList());

        // Should have 2 data points (one per model)
        assertThat(points).hasSize(2);

        // Verify OpenAI metrics
        Optional<LongPointData> openAiPoint = points.stream()
                .filter(p -> "open_ai".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)))
                .findFirst();
        assertThat(openAiPoint).isPresent();
        assertThat(openAiPoint.get().getValue()).isEqualTo(30); // 10 + 20

        // Verify Anthropic metrics
        Optional<LongPointData> anthropicPoint = points.stream()
                .filter(p -> "anthropic".equals(p.getAttributes().get(GenAiMetrics.ATTR_GEN_AI_SYSTEM)))
                .findFirst();
        assertThat(anthropicPoint).isPresent();
        assertThat(anthropicPoint.get().getValue()).isEqualTo(40); // 15 + 25
    }

    private void executeRequest(ConfiguredChatModelListener listener,
                                 String model,
                                 ModelProvider provider,
                                 int inputTokens,
                                 int outputTokens) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Test message"))
                .modelName(model)
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(
                request,
                provider,
                attributes
        );

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Test response"))
                .id("response-" + model)
                .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                .finishReason(FinishReason.STOP)
                .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                response,
                request,
                provider,
                attributes
        );

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);
    }
}
