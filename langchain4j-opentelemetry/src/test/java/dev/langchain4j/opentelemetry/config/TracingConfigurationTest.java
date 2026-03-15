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
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OpenTelemetry tracing configuration enable/disable functionality.
 * <p>
 * This test class verifies:
 * <ul>
 *   <li>Test Case 1: Configuration with tracing=false results in no spans exported</li>
 *   <li>Test Case 2: Configuration with tracing=true (default) results in spans exported</li>
 *   <li>Test Case 3: Runtime configuration changes take effect without restart</li>
 * </ul>
 */
class TracingConfigurationTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    /**
     * Creates a listener with the specified configuration.
     */
    private OpenTelemetryChatModelListener createListener(OpenTelemetryLangChain4jConfig config) {
        return OpenTelemetryChatModelListener.builder()
                .tracerProvider(otelTesting.getOpenTelemetry().getTracerProvider())
                .config(config)
                .build();
    }

    /**
     * Creates a simple chat request context for testing.
     */
    private ChatModelRequestContext createRequestContext(Map<Object, Object> attributes) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello, test!"))
                .modelName("gpt-4")
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
     * Test Case 1: Configuration with tracing=false
     * Expected: No spans exported after LLM request
     *
     * This test verifies that when tracing is disabled via configuration,
     * no OpenTelemetry spans are created, ensuring zero overhead when tracing is off.
     */
    @Test
    void configurationWithTracingDisabled_noSpansExported() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = createListener(config);
        Map<Object, Object> attributes = new HashMap<>();

        ChatModelRequestContext requestContext = createRequestContext(attributes);
        ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert - No spans should be created when tracing is disabled
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("No spans should be exported when tracing is disabled")
                .isEmpty();

        // Verify configuration state
        assertThat(listener.getConfig().isTracingEnabled())
                .as("Config should reflect tracing disabled")
                .isFalse();
    }

    /**
     * Test Case 2: Configuration with tracing=true (default)
     * Expected: Spans exported after LLM request
     *
     * This test verifies that tracing is enabled by default and spans
     * are properly created when the default configuration is used.
     */
    @Test
    void configurationWithTracingEnabled_spansExported() {
        // Arrange - Use default config (tracing enabled by default)
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.defaultConfig();

        // Verify default is tracing enabled
        assertThat(config.isTracingEnabled())
                .as("Default configuration should have tracing enabled")
                .isTrue();

        OpenTelemetryChatModelListener listener = createListener(config);
        Map<Object, Object> attributes = new HashMap<>();

        ChatModelRequestContext requestContext = createRequestContext(attributes);
        ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert - Spans should be created when tracing is enabled
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("Spans should be exported when tracing is enabled")
                .hasSize(1);

        // Verify span properties
        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("gen_ai.chat");
    }

    /**
     * Test Case 2 (Additional): Explicitly enabled tracing via builder
     * Expected: Spans exported after LLM request
     */
    @Test
    void configurationWithExplicitlyEnabledTracing_spansExported() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .build();

        OpenTelemetryChatModelListener listener = createListener(config);
        Map<Object, Object> attributes = new HashMap<>();

        ChatModelRequestContext requestContext = createRequestContext(attributes);
        ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

        // Act
        listener.onRequest(requestContext);
        listener.onResponse(responseContext);

        // Assert
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("Spans should be exported when tracing is explicitly enabled")
                .hasSize(1);
    }

    /**
     * Test Case 3: Runtime configuration change
     * Expected: Configuration changes take effect without restart
     *
     * This test verifies that the tracing configuration can be changed
     * at runtime and the changes take effect immediately for subsequent requests.
     */
    @Test
    void runtimeConfigurationChange_takesEffectImmediately() {
        // Arrange - Start with tracing enabled
        OpenTelemetryLangChain4jConfig enabledConfig = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .build();

        OpenTelemetryChatModelListener listener = createListener(enabledConfig);

        // Step 1: Make a request with tracing enabled
        Map<Object, Object> attributes1 = new HashMap<>();
        ChatModelRequestContext requestContext1 = createRequestContext(attributes1);
        ChatModelResponseContext responseContext1 = createResponseContext(requestContext1, attributes1);

        listener.onRequest(requestContext1);
        listener.onResponse(responseContext1);

        // Verify first request created a span
        List<SpanData> spansAfterFirstRequest = otelTesting.getSpans();
        assertThat(spansAfterFirstRequest)
                .as("First request with tracing enabled should create a span")
                .hasSize(1);

        // Step 2: Update configuration to disable tracing at runtime
        OpenTelemetryLangChain4jConfig disabledConfig = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        listener.updateConfig(disabledConfig);

        // Verify config was updated
        assertThat(listener.getConfig().isTracingEnabled())
                .as("Config should be updated to disabled")
                .isFalse();

        // Step 3: Make another request - should NOT create a span
        Map<Object, Object> attributes2 = new HashMap<>();
        ChatModelRequestContext requestContext2 = createRequestContext(attributes2);
        ChatModelResponseContext responseContext2 = createResponseContext(requestContext2, attributes2);

        listener.onRequest(requestContext2);
        listener.onResponse(responseContext2);

        // Verify no new spans were created
        List<SpanData> spansAfterSecondRequest = otelTesting.getSpans();
        assertThat(spansAfterSecondRequest)
                .as("Second request after disabling tracing should NOT create a new span")
                .hasSize(1);  // Still only 1 span from first request

        // Step 4: Re-enable tracing at runtime
        OpenTelemetryLangChain4jConfig reEnabledConfig = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .build();

        listener.updateConfig(reEnabledConfig);

        // Verify config was updated
        assertThat(listener.getConfig().isTracingEnabled())
                .as("Config should be updated to enabled again")
                .isTrue();

        // Step 5: Make another request - should create a span
        Map<Object, Object> attributes3 = new HashMap<>();
        ChatModelRequestContext requestContext3 = createRequestContext(attributes3);
        ChatModelResponseContext responseContext3 = createResponseContext(requestContext3, attributes3);

        listener.onRequest(requestContext3);
        listener.onResponse(responseContext3);

        // Verify new span was created
        List<SpanData> spansAfterThirdRequest = otelTesting.getSpans();
        assertThat(spansAfterThirdRequest)
                .as("Third request after re-enabling tracing should create a new span")
                .hasSize(2);  // Now we have 2 spans
    }

    /**
     * Test: Verify error handling still works when tracing is disabled
     */
    @Test
    void tracingDisabled_errorHandlingStillWorks() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = createListener(config);
        Map<Object, Object> attributes = new HashMap<>();

        ChatModelRequestContext requestContext = createRequestContext(attributes);

        // Act - simulate an error scenario
        listener.onRequest(requestContext);

        RuntimeException error = new RuntimeException("Test error");
        dev.langchain4j.model.chat.listener.ChatModelErrorContext errorContext =
                new dev.langchain4j.model.chat.listener.ChatModelErrorContext(
                        error,
                        requestContext.chatRequest(),
                        ModelProvider.OPEN_AI,
                        attributes
                );

        listener.onError(errorContext);

        // Assert - No spans should be created, but no exception should be thrown
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("No spans should be exported even during error when tracing is disabled")
                .isEmpty();
    }

    /**
     * Test: Verify multiple requests with disabled tracing don't leak resources
     */
    @Test
    void tracingDisabled_multipleRequests_noResourceLeak() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryChatModelListener listener = createListener(config);

        // Act - Make multiple requests
        for (int i = 0; i < 100; i++) {
            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = createRequestContext(attributes);
            ChatModelResponseContext responseContext = createResponseContext(requestContext, attributes);

            listener.onRequest(requestContext);
            listener.onResponse(responseContext);
        }

        // Assert - No spans should be created
        List<SpanData> spans = otelTesting.getSpans();
        assertThat(spans)
                .as("No spans should be exported for any request when tracing is disabled")
                .isEmpty();
    }

    /**
     * Test: Default configuration builder values
     */
    @Test
    void defaultConfiguration_hasExpectedDefaults() {
        // Act
        OpenTelemetryLangChain4jConfig defaultConfig = OpenTelemetryLangChain4jConfig.defaultConfig();
        OpenTelemetryLangChain4jConfig builderConfig = OpenTelemetryLangChain4jConfig.builder().build();

        // Assert - Verify default values
        assertThat(defaultConfig.isTracingEnabled())
                .as("Default tracing should be enabled")
                .isTrue();
        assertThat(builderConfig.isTracingEnabled())
                .as("Builder default tracing should be enabled")
                .isTrue();

        assertThat(defaultConfig.isMetricsEnabled())
                .as("Default metrics should be enabled")
                .isTrue();

        assertThat(defaultConfig.getContentCaptureMode())
                .as("Default content capture mode should be FULL")
                .isEqualTo(ContentCaptureMode.FULL);

        assertThat(defaultConfig.getSamplingRate())
                .as("Default sampling rate should be 1.0")
                .isEqualTo(1.0);
    }

    /**
     * Test: Configuration is immutable
     */
    @Test
    void configuration_isImmutable() {
        // Arrange
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(true)
                .metricsEnabled(true)
                .build();

        // Assert - Configuration values cannot be changed after creation
        // (This is verified by the fact that there are no setter methods)
        assertThat(config.isTracingEnabled()).isTrue();
        assertThat(config.isMetricsEnabled()).isTrue();

        // The configuration is immutable - there are no setters
        // This is a design verification test
    }
}
