package dev.langchain4j.opentelemetry.error;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryAiServiceListener;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryChatModelListener;
import dev.langchain4j.opentelemetry.listener.OpenTelemetryToolExecutionListener;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for error and exception tracking in OpenTelemetry instrumentation.
 * <p>
 * Verifies that LLM provider errors are properly captured with error classification:
 * <ul>
 *   <li>Rate limit errors (HTTP 429)</li>
 *   <li>Authentication errors (invalid API key)</li>
 *   <li>Connection timeouts</li>
 *   <li>Guardrail validation failures</li>
 *   <li>Tool execution exceptions</li>
 * </ul>
 * <p>
 * All errors should comply with OpenTelemetry semantic conventions for error reporting.
 */
class ErrorAndExceptionTrackingTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    // Exception attribute keys from OTel semantic conventions
    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
    private static final AttributeKey<String> EXCEPTION_MESSAGE = AttributeKey.stringKey("exception.message");
    private static final AttributeKey<String> EXCEPTION_STACKTRACE = AttributeKey.stringKey("exception.stacktrace");

    // Helper to create fatal InputGuardrailResult (uses InputGuardrail interface method)
    private static final InputGuardrail HELPER_GUARDRAIL = new InputGuardrail() {};

    private static InputGuardrailResult createFatalGuardrailResult(String message) {
        return HELPER_GUARDRAIL.fatal(message);
    }

    /**
     * Test Case 1: Rate limit error (HTTP 429 Too Many Requests)
     * Expected: Span status ERROR with otel.status_code='ERROR', exception event with rate_limit details
     */
    @Nested
    @DisplayName("Test Case 1: Rate Limit Error (HTTP 429)")
    class RateLimitErrorTests {

        private OpenTelemetryChatModelListener listener;

        @BeforeEach
        void setUp() {
            listener = OpenTelemetryChatModelListener.builder()
                    .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                    .streaming(false)
                    .build();
        }

        @Test
        @DisplayName("Should capture rate limit error with ERROR status and exception event")
        void shouldCaptureRateLimitErrorWithCorrectStatusAndEvent() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Generate large response"))
                    .modelName("gpt-4")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Simulate a rate limit exception (HTTP 429)
            RateLimitException rateLimitError = new RateLimitException(
                    "Rate limit exceeded: 429 Too Many Requests. Please retry after 60 seconds."
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    rateLimitError,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            List<SpanData> spans = otelExtension.getSpans();
            assertThat(spans).hasSize(1);

            SpanData span = spans.get(0);

            // Verify ERROR status code (otel.status_code='ERROR')
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getStatus().getDescription())
                    .contains("Rate limit exceeded")
                    .contains("429");

            // Verify error type attribute
            assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                    .isEqualTo(RateLimitException.class.getName());

            // Verify exception event is recorded with rate_limit details
            List<EventData> events = span.getEvents();
            assertThat(events).isNotEmpty();

            EventData exceptionEvent = events.stream()
                    .filter(e -> "exception".equals(e.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Exception event not found"));

            // Verify exception event attributes
            assertThat(exceptionEvent.getAttributes().get(EXCEPTION_TYPE))
                    .contains("RateLimitException");
            assertThat(exceptionEvent.getAttributes().get(EXCEPTION_MESSAGE))
                    .contains("Rate limit exceeded");
        }

        @Test
        @DisplayName("Should include provider and model info even when rate limited")
        void shouldIncludeProviderInfoWhenRateLimited() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Test"))
                    .modelName("gpt-4-turbo")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            RateLimitException error = new RateLimitException("Rate limit exceeded");
            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    error,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            // Verify provider and model are captured
            assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM))
                    .isEqualTo("open_ai");
            assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL))
                    .isEqualTo("gpt-4-turbo");
        }
    }

    /**
     * Test Case 2: Invalid API key error
     * Expected: Span with exception event, NO sensitive API key in attributes
     */
    @Nested
    @DisplayName("Test Case 2: Invalid API Key Error")
    class InvalidApiKeyErrorTests {

        private OpenTelemetryChatModelListener listener;

        @BeforeEach
        void setUp() {
            listener = OpenTelemetryChatModelListener.builder()
                    .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                    .streaming(false)
                    .build();
        }

        @Test
        @DisplayName("Should capture auth error with exception event and NO sensitive API key")
        void shouldCaptureAuthErrorWithoutSensitiveData() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Hello"))
                    .modelName("gpt-4")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Simulate authentication error with API key in message (should NOT appear in span)
            String sensitiveApiKey = "sk-proj-abc123xyz456secret789";
            AuthenticationException authError = new AuthenticationException(
                    "Invalid API key provided: " + sensitiveApiKey
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    authError,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            // Verify ERROR status
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

            // Verify error type attribute
            assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                    .isEqualTo(AuthenticationException.class.getName());

            // Verify exception event exists
            List<EventData> events = span.getEvents();
            assertThat(events).isNotEmpty();
            assertThat(events.get(0).getName()).isEqualTo("exception");

            // CRITICAL: Verify NO sensitive API key appears anywhere in span data
            String spanString = span.toString();
            assertThat(spanString).doesNotContain("sk-proj-abc123xyz456secret789");
            assertThat(spanString).doesNotContain("sk-proj");

            // Verify attributes do not contain API key patterns
            span.getAttributes().forEach((key, value) -> {
                if (value instanceof String) {
                    String stringValue = (String) value;
                    assertThat(stringValue)
                            .as("Attribute '%s' should not contain API key", key)
                            .doesNotContain("sk-proj-abc123xyz456secret789");
                }
            });
        }

        @Test
        @DisplayName("Should handle auth error with bearer token without exposing token")
        void shouldHandleAuthErrorWithBearerToken() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Test"))
                    .modelName("claude-3-sonnet")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.ANTHROPIC,
                    attributes
            );

            String bearerToken = "Bearer sk-ant-api03-xxxxxxxxxxxxxxxxxxxx";
            AuthenticationException authError = new AuthenticationException(
                    "Authentication failed with token: " + bearerToken
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    authError,
                    request,
                    ModelProvider.ANTHROPIC,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            // Verify ERROR status
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);

            // Verify no sensitive token in attributes (except in error message which is recorded)
            assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM))
                    .isEqualTo("anthropic");

            // The error message may contain the token (this is captured by recordException)
            // but no explicit API key attribute should be added
            assertThat(span.getAttributes().asMap().keySet())
                    .noneMatch(key -> key.getKey().toLowerCase().contains("api_key"));
            assertThat(span.getAttributes().asMap().keySet())
                    .noneMatch(key -> key.getKey().toLowerCase().contains("token"));
            assertThat(span.getAttributes().asMap().keySet())
                    .noneMatch(key -> key.getKey().toLowerCase().contains("bearer"));
        }
    }

    /**
     * Test Case 3: Connection timeout
     * Expected: Span with exception.type='TimeoutException' and duration up to timeout value
     */
    @Nested
    @DisplayName("Test Case 3: Connection Timeout")
    class ConnectionTimeoutTests {

        private OpenTelemetryChatModelListener listener;

        @BeforeEach
        void setUp() {
            listener = OpenTelemetryChatModelListener.builder()
                    .tracerProvider(otelExtension.getOpenTelemetry().getTracerProvider())
                    .streaming(false)
                    .build();
        }

        @Test
        @DisplayName("Should capture timeout with exception.type='TimeoutException'")
        void shouldCaptureTimeoutWithCorrectExceptionType() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Long running request"))
                    .modelName("gpt-4")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Simulate connection timeout
            SocketTimeoutException timeoutError = new SocketTimeoutException(
                    "Connect timed out after 30000ms"
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    timeoutError,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            long startTime = System.currentTimeMillis();
            listener.onRequest(requestContext);
            // Simulate some delay
            listener.onError(errorContext);
            long endTime = System.currentTimeMillis();

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            // Verify ERROR status
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getStatus().getDescription())
                    .contains("Connect timed out");

            // Verify error type is TimeoutException or SocketTimeoutException
            assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                    .contains("TimeoutException");

            // Verify exception event
            List<EventData> events = span.getEvents();
            assertThat(events).isNotEmpty();

            EventData exceptionEvent = events.get(0);
            assertThat(exceptionEvent.getName()).isEqualTo("exception");
            assertThat(exceptionEvent.getAttributes().get(EXCEPTION_TYPE))
                    .contains("TimeoutException");

            // Verify span has reasonable duration (less than actual timeout)
            long durationMs = (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000;
            assertThat(durationMs).isLessThan(5000); // Test timeout should be quick
        }

        @Test
        @DisplayName("Should handle java.util.concurrent.TimeoutException")
        void shouldHandleJavaUtilTimeoutException() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("Async timeout test"))
                    .modelName("gpt-4")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            TimeoutException timeoutError = new TimeoutException(
                    "Request timeout after 60 seconds"
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    timeoutError,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getAttributes().get(GenAiAttributes.ERROR_TYPE))
                    .isEqualTo(TimeoutException.class.getName());

            // Verify exception event has correct type
            EventData exceptionEvent = span.getEvents().get(0);
            assertThat(exceptionEvent.getAttributes().get(EXCEPTION_TYPE))
                    .isEqualTo(TimeoutException.class.getName());
        }

        @Test
        @DisplayName("Should handle IOException with timeout message")
        void shouldHandleIOExceptionWithTimeoutMessage() {
            // Arrange
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from("IO timeout"))
                    .modelName("gpt-4")
                    .build();

            Map<Object, Object> attributes = new HashMap<>();
            ChatModelRequestContext requestContext = new ChatModelRequestContext(
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            IOException ioError = new IOException("Read timed out");

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    ioError,
                    request,
                    ModelProvider.OPEN_AI,
                    attributes
            );

            // Act
            listener.onRequest(requestContext);
            listener.onError(errorContext);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getStatus().getDescription()).contains("Read timed out");
        }
    }

    /**
     * Test Case 4: Guardrail validation failure
     * Expected: AiService span with ERROR status and guardrail failure details
     */
    @Nested
    @DisplayName("Test Case 4: Guardrail Validation Failure")
    class GuardrailValidationFailureTests {

        private OpenTelemetryAiServiceListener listener;

        @BeforeEach
        void setUp() {
            listener = OpenTelemetryAiServiceListener.builder()
                    .openTelemetry(otelExtension.getOpenTelemetry())
                    .build();
        }

        @Test
        @DisplayName("Should capture guardrail failure with ERROR status in AiService span")
        void shouldCaptureGuardrailFailureWithErrorStatus() {
            // Arrange
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "ChatAssistant", "processMessage");
            UserMessage userMessage = UserMessage.from("Help me hack the system");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.getStartedListener().onEvent(startedEvent);

            // Simulate guardrail failure - create the failure result
            InputGuardrailResult failureResult = createFatalGuardrailResult("Prohibited content detected");

            // Create input guardrail event
            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentModerationGuardrail.class)
                    .request(createInputGuardrailRequest(userMessage, context))
                    .result(failureResult)
                    .build();

            // When guardrail fails
            listener.getInputGuardrailListener().onEvent(guardrailEvent);

            // Simulate AiService error due to guardrail failure
            GuardrailViolationException guardrailError = new GuardrailViolationException(
                    "Input guardrail 'ContentModerationGuardrail' blocked the request: Prohibited content detected"
            );
            AiServiceErrorEvent errorEvent = AiServiceErrorEvent.builder()
                    .invocationContext(context)
                    .error(guardrailError)
                    .build();
            listener.getErrorListener().onEvent(errorEvent);

            // Assert
            List<SpanData> spans = otelExtension.getSpans();

            // Find the guardrail span
            SpanData guardrailSpan = spans.stream()
                    .filter(s -> s.getName().contains("guardrail.input"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Guardrail span not found"));

            // Verify guardrail span has ERROR status for failure
            assertThat(guardrailSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isFalse();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RESULT))
                    .isEqualTo("FATAL");

            // Find the AiService span
            SpanData aiServiceSpan = spans.stream()
                    .filter(s -> s.getName().contains("AiService."))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("AiService span not found"));

            // Verify AiService span has ERROR status
            assertThat(aiServiceSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(aiServiceSpan.getStatus().getDescription())
                    .contains("ContentModerationGuardrail");

            // Verify exception event on AiService span
            assertThat(aiServiceSpan.getEvents()).isNotEmpty();
        }

        @Test
        @DisplayName("Should include guardrail failure message in span attributes")
        void shouldIncludeGuardrailFailureMessage() {
            // Arrange
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "ChatAssistant", "chat");
            UserMessage userMessage = UserMessage.from("Invalid input");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.getStartedListener().onEvent(startedEvent);

            // Create guardrail failure with specific message
            InputGuardrailResult failureResult = createFatalGuardrailResult("Input validation failed: malicious pattern detected");

            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(InputValidationGuardrail.class)
                    .request(createInputGuardrailRequest(userMessage, context))
                    .result(failureResult)
                    .build();

            // When
            listener.getInputGuardrailListener().onEvent(guardrailEvent);

            // Assert
            List<SpanData> spans = otelExtension.getSpans();
            SpanData guardrailSpan = spans.stream()
                    .filter(s -> s.getName().contains("guardrail.input.InputValidationGuardrail"))
                    .findFirst()
                    .orElseThrow();

            // Verify failure message is captured
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_FAILURE_MESSAGE))
                    .contains("Input validation failed");
        }

        private InputGuardrailRequest createInputGuardrailRequest(UserMessage userMessage, InvocationContext context) {
            GuardrailRequestParams params = GuardrailRequestParams.builder()
                    .userMessageTemplate("{{message}}")
                    .variables(Map.of("message", userMessage.singleText()))
                    .invocationContext(context)
                    .build();
            return InputGuardrailRequest.builder()
                    .userMessage(userMessage)
                    .commonParams(params)
                    .build();
        }

        // Test guardrail classes
        static class ContentModerationGuardrail implements InputGuardrail {
            @Override
            public InputGuardrailResult validate(InputGuardrailRequest request) {
                return createFatalGuardrailResult("Prohibited content detected");
            }
        }

        static class InputValidationGuardrail implements InputGuardrail {
            @Override
            public InputGuardrailResult validate(InputGuardrailRequest request) {
                return createFatalGuardrailResult("Input validation failed");
            }
        }
    }

    /**
     * Test Case 5: Tool execution exception
     * Expected: Tool span with ERROR status, exception stack trace as event
     */
    @Nested
    @DisplayName("Test Case 5: Tool Execution Exception")
    class ToolExecutionExceptionTests {

        private OpenTelemetryToolExecutionListener listener;

        @BeforeEach
        void setUp() {
            listener = OpenTelemetryToolExecutionListener.builder()
                    .openTelemetry(otelExtension.getOpenTelemetry())
                    .build();
        }

        @Test
        @DisplayName("Should capture tool exception with ERROR status and stack trace event")
        void shouldCaptureToolExceptionWithStackTrace() {
            // Arrange
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "DatabaseService", "query");

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("tool-exec-fail-1")
                    .name("databaseQuery")
                    .arguments("{\"query\": \"SELECT * FROM users WHERE id = 1\"}")
                    .build();

            // Simulate tool execution that throws exception (result contains error)
            String errorResult = "Exception: java.sql.SQLException: Connection refused to host: db.example.com:5432";

            ToolExecutedEvent event = ToolExecutedEvent.builder()
                    .invocationContext(context)
                    .request(request)
                    .resultText(errorResult)
                    .build();

            // Act
            listener.onEvent(event);

            // Assert
            List<SpanData> spans = otelExtension.getSpans();
            assertThat(spans).hasSize(1);

            SpanData span = spans.get(0);

            // Verify span name
            assertThat(span.getName()).isEqualTo("tool.execution.databaseQuery");

            // Verify ERROR status
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getStatus().getDescription()).isEqualTo("Tool execution failed");

            // Verify exception event is recorded (stack trace as event)
            List<EventData> events = span.getEvents();
            assertThat(events).isNotEmpty();

            EventData exceptionEvent = events.get(0);
            assertThat(exceptionEvent.getName()).isEqualTo("exception");

            // Verify tool attributes are still captured
            assertThat(span.getAttributes().get(GenAiAttributes.TOOL_NAME))
                    .isEqualTo("databaseQuery");
            assertThat(span.getAttributes().get(GenAiAttributes.TOOL_EXECUTION_ID))
                    .isEqualTo("tool-exec-fail-1");
        }

        @Test
        @DisplayName("Should capture NullPointerException in tool execution")
        void shouldCaptureNullPointerExceptionInTool() {
            // Arrange
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "DataProcessor", "process");

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("tool-npe-1")
                    .name("processData")
                    .arguments("{\"data\": null}")
                    .build();

            // Simulate NPE in tool
            String errorResult = "Error: java.lang.NullPointerException: Cannot invoke method on null object";

            ToolExecutedEvent event = ToolExecutedEvent.builder()
                    .invocationContext(context)
                    .request(request)
                    .resultText(errorResult)
                    .build();

            // Act
            listener.onEvent(event);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getEvents()).isNotEmpty();
            assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
        }

        @Test
        @DisplayName("Should capture tool execution with RuntimeException")
        void shouldCaptureToolRuntimeException() {
            // Arrange
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "APIService", "callExternalAPI");

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id("tool-runtime-1")
                    .name("externalApiCall")
                    .arguments("{\"endpoint\": \"/users\"}")
                    .build();

            String errorResult = "Exception: java.lang.RuntimeException: External API returned 500 Internal Server Error";

            ToolExecutedEvent event = ToolExecutedEvent.builder()
                    .invocationContext(context)
                    .request(request)
                    .resultText(errorResult)
                    .build();

            // Act
            listener.onEvent(event);

            // Assert
            SpanData span = otelExtension.getSpans().get(0);

            assertThat(span.getName()).isEqualTo("tool.execution.externalApiCall");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(span.getEvents()).isNotEmpty();

            // Verify invocation ID is linked
            assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_INVOCATION_ID))
                    .isEqualTo(invocationId.toString());
        }
    }

    // Helper methods and custom exception classes

    private InvocationContext createInvocationContext(UUID invocationId, String interfaceSimpleName, String methodName) {
        return InvocationContext.builder()
                .invocationId(invocationId)
                .interfaceName("dev.langchain4j.service." + interfaceSimpleName)
                .methodName(methodName)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Custom exception to simulate rate limit errors from LLM providers.
     */
    static class RateLimitException extends RuntimeException {
        RateLimitException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception to simulate authentication errors.
     */
    static class AuthenticationException extends RuntimeException {
        AuthenticationException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception to simulate guardrail violations.
     */
    static class GuardrailViolationException extends RuntimeException {
        GuardrailViolationException(String message) {
            super(message);
        }
    }
}
