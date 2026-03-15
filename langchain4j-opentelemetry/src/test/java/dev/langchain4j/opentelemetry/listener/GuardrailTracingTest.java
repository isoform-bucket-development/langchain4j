package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for guardrail execution tracing in {@link OpenTelemetryAiServiceListener}.
 *
 * Verifies that input guardrail validations create appropriate spans
 * with proper attributes, status codes, and hierarchical structure.
 *
 * Note: Output guardrail tests are limited because OutputGuardrailRequest requires
 * complex dependencies (ChatResponse, ChatExecutor) that are difficult to mock.
 * Also, failure scenario tests are limited because guardrail result Failure
 * constructors are package-private in langchain4j-core.
 */
class GuardrailTracingTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    private OpenTelemetryAiServiceListener createListener() {
        return OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .build();
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

    @Test
    @DisplayName("Should create guardrail span with status OK and pass=true attribute")
    void shouldCreateGuardrailSpanWithStatusOkWhenPassing() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
        UserMessage userMessage = UserMessage.from("Hello, how are you?");

        // Start AiService span (parent span for guardrail)
        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();
        listener.getStartedListener().onEvent(startedEvent);

        // Create input guardrail event that passes
        InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                .invocationContext(context)
                .guardrailClass(ProhibitedWordsInputGuardrail.class)
                .request(createInputGuardrailRequest(userMessage, context))
                .result(InputGuardrailResult.success())
                .build();

        // When
        listener.getInputGuardrailListener().onEvent(guardrailEvent);

        // Complete the AiService span
        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("I'm doing well!")
                .build();
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(2);

        // Find the guardrail span
        SpanData guardrailSpan = spans.stream()
                .filter(s -> s.getName().equals("guardrail.input.ProhibitedWordsInputGuardrail"))
                .findFirst()
                .orElseThrow();

        // Verify span attributes
        assertThat(guardrailSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isTrue();
        assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_NAME))
                .isEqualTo("ProhibitedWordsInputGuardrail");
        assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_TYPE))
                .isEqualTo("input");
        assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RESULT))
                .isEqualTo("SUCCESS");

        // Verify hierarchical structure - guardrail should be child of AiService
        SpanData aiServiceSpan = spans.stream()
                .filter(s -> s.getName().equals("AiService.chat"))
                .findFirst()
                .orElseThrow();
        assertThat(guardrailSpan.getParentSpanId()).isEqualTo(aiServiceSpan.getSpanId());
    }

    @Test
    @DisplayName("GenAiSpanNames.guardrailInput should generate correct span names")
    void shouldGenerateCorrectInputGuardrailSpanNames() {
        assertThat(GenAiSpanNames.guardrailInput("ContentFilter")).isEqualTo("guardrail.input.ContentFilter");
        assertThat(GenAiSpanNames.guardrailInput("ProhibitedWordsGuardrail")).isEqualTo("guardrail.input.ProhibitedWordsGuardrail");
    }

    @Test
    @DisplayName("GenAiSpanNames.guardrailOutput should generate correct span names")
    void shouldGenerateCorrectOutputGuardrailSpanNames() {
        assertThat(GenAiSpanNames.guardrailOutput("ContentFilter")).isEqualTo("guardrail.output.ContentFilter");
        assertThat(GenAiSpanNames.guardrailOutput("SafetyGuardrail")).isEqualTo("guardrail.output.SafetyGuardrail");
    }

    @Test
    @DisplayName("Should create separate spans for multiple input guardrail executions")
    void shouldCreateSeparateSpansForMultipleInputGuardrails() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
        UserMessage userMessage = UserMessage.from("Hello");

        // Start AiService span
        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();
        listener.getStartedListener().onEvent(startedEvent);

        // First input guardrail
        InputGuardrailExecutedEvent guardrailEvent1 = InputGuardrailExecutedEvent.builder()
                .invocationContext(context)
                .guardrailClass(ProhibitedWordsInputGuardrail.class)
                .request(createInputGuardrailRequest(userMessage, context))
                .result(InputGuardrailResult.success())
                .build();
        listener.getInputGuardrailListener().onEvent(guardrailEvent1);

        // Second input guardrail
        InputGuardrailExecutedEvent guardrailEvent2 = InputGuardrailExecutedEvent.builder()
                .invocationContext(context)
                .guardrailClass(ContentLengthInputGuardrail.class)
                .request(createInputGuardrailRequest(userMessage, context))
                .result(InputGuardrailResult.success())
                .build();
        listener.getInputGuardrailListener().onEvent(guardrailEvent2);

        // Complete AiService
        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Hi there!")
                .build();
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();

        // Should have 3 spans: AiService + 2 input guardrails
        assertThat(spans).hasSize(3);

        // Verify all guardrail spans exist
        assertThat(spans.stream()
                .filter(s -> s.getName().equals("guardrail.input.ProhibitedWordsInputGuardrail"))
                .count()).isEqualTo(1);
        assertThat(spans.stream()
                .filter(s -> s.getName().equals("guardrail.input.ContentLengthInputGuardrail"))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should not create guardrail spans when tracing is disabled")
    void shouldNotCreateGuardrailSpansWhenTracingDisabled() {
        // Given
        OpenTelemetryAiServiceListener disabledListener = OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .config(dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig.builder()
                        .tracingEnabled(false)
                        .build())
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
        UserMessage userMessage = UserMessage.from("Hello");

        InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                .invocationContext(context)
                .guardrailClass(ProhibitedWordsInputGuardrail.class)
                .request(createInputGuardrailRequest(userMessage, context))
                .result(InputGuardrailResult.success())
                .build();

        int spanCountBefore = otelExtension.getSpans().size();

        // When
        disabledListener.getInputGuardrailListener().onEvent(guardrailEvent);

        // Then - No new spans should be created
        assertThat(otelExtension.getSpans().size()).isEqualTo(spanCountBefore);
    }

    // Helper method to create InvocationContext
    private InvocationContext createInvocationContext(UUID invocationId, String interfaceSimpleName, String methodName) {
        return InvocationContext.builder()
                .invocationId(invocationId)
                .interfaceName("dev.langchain4j.service." + interfaceSimpleName)
                .methodName(methodName)
                .timestamp(Instant.now())
                .build();
    }

    // Test guardrail classes for type-safe event creation
    static class ProhibitedWordsInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailRequest request) {
            return InputGuardrailResult.success();
        }
    }

    static class ContentLengthInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailRequest request) {
            return InputGuardrailResult.success();
        }
    }

    static class ContentFilterOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            return OutputGuardrailResult.success();
        }
    }
}
