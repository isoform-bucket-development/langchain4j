package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for tool execution tracing via {@link OpenTelemetryToolExecutionListener}.
 *
 * <p>Verifies that tool executions create proper child spans with:
 * <ul>
 *   <li>Tool name in span name</li>
 *   <li>Tool arguments and results in attributes</li>
 *   <li>Proper error handling and exception recording</li>
 *   <li>Sensitive argument sanitization</li>
 * </ul>
 *
 * <p>Span hierarchy: [AiService] -> [gen_ai.chat] -> [tool.execution.toolName]
 */
class ToolExecutionTracingTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    // Test Case 1: Tool 'calculator' with arguments
    @Test
    @DisplayName("TC1: Should create span 'tool.execution.calculator' with arguments and result '8' in attributes")
    void shouldCreateToolSpanWithArgumentsAndResult() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Calculator", "calculate");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-exec-1")
                .name("calculator")
                .arguments("{\"a\": 5, \"b\": 3, \"operation\": \"add\"}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("8")
                .build();

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify span name follows convention
        assertThat(span.getName()).isEqualTo("tool.execution.calculator");

        // Verify span status is OK
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify tool name attribute
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_NAME))
                .isEqualTo("calculator");

        // Verify tool execution ID
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_EXECUTION_ID))
                .isEqualTo("tool-exec-1");

        // Verify arguments are captured
        String arguments = span.getAttributes().get(GenAiAttributes.TOOL_ARGUMENTS);
        assertThat(arguments)
                .contains("\"a\": 5")
                .contains("\"b\": 3")
                .contains("\"operation\": \"add\"");

        // Verify result is captured
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_RESULT))
                .isEqualTo("8");

        // Verify operation name
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME))
                .isEqualTo("tool_execution");

        // Verify invocation ID is linked
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_INVOCATION_ID))
                .isEqualTo(invocationId.toString());
    }

    @Test
    @DisplayName("TC1b: GenAiSpanNames.toolExecution should generate correct span names")
    void shouldGenerateCorrectToolSpanNames() {
        assertThat(GenAiSpanNames.toolExecution("calculator")).isEqualTo("tool.execution.calculator");
        assertThat(GenAiSpanNames.toolExecution("weatherLookup")).isEqualTo("tool.execution.weatherLookup");
        assertThat(GenAiSpanNames.toolExecution("database_query")).isEqualTo("tool.execution.database_query");
    }

    // Test Case 2: Multiple tool executions in single LLM turn
    @Test
    @DisplayName("TC2: Should create multiple sibling tool spans under same gen_ai.chat span")
    void shouldCreateMultipleSiblingToolSpans() {
        // Given - Same invocation context for all tool executions (same LLM turn)
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "MultiToolAssistant", "processQuery");

        // First tool execution: calculator add
        ToolExecutionRequest addRequest = ToolExecutionRequest.builder()
                .id("tool-exec-1")
                .name("calculator")
                .arguments("{\"a\": 5, \"b\": 3, \"operation\": \"add\"}")
                .build();

        ToolExecutedEvent addEvent = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(addRequest)
                .resultText("8")
                .build();

        // Second tool execution: calculator multiply
        ToolExecutionRequest multiplyRequest = ToolExecutionRequest.builder()
                .id("tool-exec-2")
                .name("calculator")
                .arguments("{\"a\": 4, \"b\": 7, \"operation\": \"multiply\"}")
                .build();

        ToolExecutedEvent multiplyEvent = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(multiplyRequest)
                .resultText("28")
                .build();

        // Third tool execution: different tool (weatherLookup)
        ToolExecutionRequest weatherRequest = ToolExecutionRequest.builder()
                .id("tool-exec-3")
                .name("weatherLookup")
                .arguments("{\"city\": \"New York\"}")
                .build();

        ToolExecutedEvent weatherEvent = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(weatherRequest)
                .resultText("Sunny, 72F")
                .build();

        // When - Execute all three tool events
        listener.onEvent(addEvent);
        listener.onEvent(multiplyEvent);
        listener.onEvent(weatherEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(3);

        // Verify all spans reference the same invocation ID
        assertThat(spans).allMatch(span ->
                invocationId.toString().equals(span.getAttributes().get(GenAiAttributes.AISERVICE_INVOCATION_ID))
        );

        // Verify span names
        List<String> spanNames = spans.stream()
                .map(SpanData::getName)
                .collect(Collectors.toList());

        assertThat(spanNames).containsExactly(
                "tool.execution.calculator",
                "tool.execution.calculator",
                "tool.execution.weatherLookup"
        );

        // Verify results are captured correctly
        List<String> results = spans.stream()
                .map(span -> span.getAttributes().get(GenAiAttributes.TOOL_RESULT))
                .collect(Collectors.toList());

        assertThat(results).containsExactly("8", "28", "Sunny, 72F");

        // Verify all spans have OK status
        assertThat(spans).allMatch(span ->
                span.getStatus().getStatusCode() == StatusCode.OK
        );
    }

    // Test Case 3: Tool execution throwing exception
    @Test
    @DisplayName("TC3: Should create tool span with ERROR status and exception event recorded")
    void shouldRecordErrorStatusAndExceptionEvent() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "DatabaseService", "query");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-exec-error")
                .name("databaseQuery")
                .arguments("{\"query\": \"SELECT * FROM users\"}")
                .build();

        // Simulate an error result (exception message)
        String errorResult = "Error: java.sql.SQLException: Connection refused";

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText(errorResult)
                .build();

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify span name
        assertThat(span.getName()).isEqualTo("tool.execution.databaseQuery");

        // Verify ERROR status
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("Tool execution failed");

        // Verify exception event is recorded
        List<EventData> events = span.getEvents();
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getName()).isEqualTo("exception");

        // Verify tool attributes are still captured
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_NAME))
                .isEqualTo("databaseQuery");
    }

    // Test Case 4: Tool with sensitive argument (password field)
    @Test
    @DisplayName("TC4: Should redact sensitive argument (password) in span attributes")
    void shouldRedactSensitivePasswordArgument() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "AuthService", "login");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-auth-1")
                .name("authenticateUser")
                .arguments("{\"username\": \"john_doe\", \"password\": \"super_secret_123\"}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("Authentication successful")
                .build();

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);

        // Verify span name
        assertThat(span.getName()).isEqualTo("tool.execution.authenticateUser");

        // Verify sensitive argument is redacted
        String arguments = span.getAttributes().get(GenAiAttributes.TOOL_ARGUMENTS);
        assertThat(arguments)
                .contains("\"username\": \"john_doe\"")  // Non-sensitive preserved
                .contains("\"password\": \"[REDACTED]\"")  // Sensitive redacted
                .doesNotContain("super_secret_123");  // Original value not present
    }

    @Test
    @DisplayName("TC4b: Should redact various sensitive field patterns")
    void shouldRedactVariousSensitivePatterns() {
        OpenTelemetryToolExecutionListener listener = createListener();

        // Test the sanitization directly for various sensitive patterns
        String[][] testCases = {
                {"api_key", "sk-abc123xyz"},
                {"apiKey", "AKIA123456"},
                {"secret", "my-secret-value"},
                {"token", "bearer-token-xyz"},
                {"access_token", "oauth-access-token"},
                {"private_key", "-----BEGIN PRIVATE KEY-----"},
                {"credential", "user:pass"},
                {"auth", "basic-auth-header"}
        };

        for (String[] testCase : testCases) {
            String fieldName = testCase[0];
            String originalValue = testCase[1];

            String arguments = String.format("{\"%s\": \"%s\", \"safe_field\": \"safe_value\"}",
                    fieldName, originalValue);

            // Test the sanitization directly
            String sanitized = listener.sanitizeArguments(arguments);

            assertThat(sanitized)
                    .as("Field '%s' should be redacted", fieldName)
                    .contains("\"" + fieldName + "\": \"[REDACTED]\"")
                    .doesNotContain(originalValue)
                    .contains("\"safe_field\": \"safe_value\"");
        }
    }

    @Test
    @DisplayName("TC4c: Should not redact non-sensitive fields")
    void shouldNotRedactNonSensitiveFields() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "DataService", "getData");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-data-1")
                .name("fetchData")
                .arguments("{\"name\": \"John\", \"age\": 30, \"city\": \"New York\"}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("Data fetched")
                .build();

        // When
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.get(0);

        String arguments = span.getAttributes().get(GenAiAttributes.TOOL_ARGUMENTS);
        assertThat(arguments)
                .contains("\"name\": \"John\"")
                .contains("\"age\": 30")
                .contains("\"city\": \"New York\"")
                .doesNotContain("[REDACTED]");
    }

    @Test
    @DisplayName("Should not create spans when tracing is disabled")
    void shouldNotCreateSpansWhenTracingDisabled() {
        // Given
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryToolExecutionListener disabledListener = OpenTelemetryToolExecutionListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .config(config)
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Service", "method");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("testTool")
                .arguments("{}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("result")
                .build();

        int spanCountBefore = otelExtension.getSpans().size();

        // When
        disabledListener.onEvent(event);

        // Then - No new spans should be created
        assertThat(otelExtension.getSpans().size()).isEqualTo(spanCountBefore);
    }

    @Test
    @DisplayName("Should not capture arguments when content capture is NONE")
    void shouldNotCaptureArgumentsWhenContentCaptureNone() {
        // Given
        OpenTelemetryToolExecutionListener noCaptureListener = OpenTelemetryToolExecutionListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .contentCaptureMode(ContentCaptureMode.NONE)
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Service", "method");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("noCaptureTestTool")
                .arguments("{\"sensitive\": \"data\"}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("some result")
                .build();

        // When
        noCaptureListener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("tool.execution.noCaptureTestTool"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        // Span should exist but without arguments and result
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_NAME)).isEqualTo("noCaptureTestTool");
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_ARGUMENTS)).isNull();
        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_RESULT)).isNull();
    }

    @Test
    @DisplayName("Should handle null arguments gracefully")
    void shouldHandleNullArguments() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Service", "method");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("noArgsTool")
                .arguments(null)
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("result")
                .build();

        // When - Should not throw
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("tool.execution.noArgsTool"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_ARGUMENTS)).isNull();
    }

    @Test
    @DisplayName("Should handle null tool execution ID")
    void shouldHandleNullToolExecutionId() {
        // Given
        OpenTelemetryToolExecutionListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Service", "method");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(null)
                .name("noIdTool")
                .arguments("{}")
                .build();

        ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(context)
                .request(request)
                .resultText("result")
                .build();

        // When - Should not throw
        listener.onEvent(event);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("tool.execution.noIdTool"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getAttributes().get(GenAiAttributes.TOOL_EXECUTION_ID)).isNull();
    }

    // Helper methods
    private OpenTelemetryToolExecutionListener createListener() {
        return OpenTelemetryToolExecutionListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .build();
    }

    private InvocationContext createInvocationContext(UUID invocationId, String interfaceSimpleName, String methodName) {
        return InvocationContext.builder()
                .invocationId(invocationId)
                .interfaceName("dev.langchain4j.service." + interfaceSimpleName)
                .methodName(methodName)
                .timestamp(Instant.now())
                .build();
    }
}
