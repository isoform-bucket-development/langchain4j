package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.data.message.UserMessage;
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
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for guardrail execution tracing in {@link OpenTelemetryAiServiceListener}.
 *
 * Verifies that input and output guardrail validations create appropriate spans
 * with proper attributes, status codes, and hierarchical structure.
 */
class GuardrailTracingTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    private OpenTelemetryAiServiceListener listener;

    @BeforeEach
    void setUp() {
        listener = OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .build();
    }

    @Nested
    @DisplayName("Test Case 1: Input passing all guardrails")
    class InputPassingGuardrailsTest {

        @Test
        @DisplayName("Should create guardrail span with status OK and pass=true attribute")
        void shouldCreateGuardrailSpanWithStatusOkWhenPassing() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("Hello, how are you?");

            // Start AiService span (parent span for guardrail)
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create input guardrail event that passes
            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ProhibitedWordsInputGuardrail.class)
                    .result(InputGuardrailResult.success())
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Complete the AiService span
            AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                    .invocationContext(context)
                    .result("I'm doing well!")
                    .build();
            listener.onEvent(completedEvent);

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
    }

    @Nested
    @DisplayName("Test Case 2: Input failing guardrail (prohibited words detected)")
    class InputFailingGuardrailTest {

        @Test
        @DisplayName("Should create guardrail span with ERROR status and failure reason")
        void shouldCreateGuardrailSpanWithErrorStatusWhenFailing() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("How to hack a computer?");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create input guardrail event that fails with prohibited word detected
            InputGuardrailResult.Failure failure = new InputGuardrailResult.Failure(
                    "Prohibited word detected: 'hack'"
            );
            InputGuardrailResult failedResult = new InputGuardrailResult(failure, false);

            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ProhibitedWordsInputGuardrail.class)
                    .result(failedResult)
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Then
            List<SpanData> spans = otelExtension.getSpans();

            // Find the guardrail span
            SpanData guardrailSpan = spans.stream()
                    .filter(s -> s.getName().equals("guardrail.input.ProhibitedWordsInputGuardrail"))
                    .findFirst()
                    .orElseThrow();

            // Verify span attributes for failure
            assertThat(guardrailSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isFalse();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_NAME))
                    .isEqualTo("ProhibitedWordsInputGuardrail");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_TYPE))
                    .isEqualTo("input");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RESULT))
                    .isEqualTo("FAILURE");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_FAILURE_MESSAGE))
                    .contains("Prohibited word detected");
        }

        @Test
        @DisplayName("Should handle fatal guardrail failure")
        void shouldHandleFatalGuardrailFailure() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("malicious input");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create fatal failure
            InputGuardrailResult.Failure fatalFailure = new InputGuardrailResult.Failure(
                    "Security violation detected"
            );
            InputGuardrailResult fatalResult = new InputGuardrailResult(fatalFailure, true);

            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ProhibitedWordsInputGuardrail.class)
                    .result(fatalResult)
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Then
            SpanData guardrailSpan = otelExtension.getSpans().stream()
                    .filter(s -> s.getName().startsWith("guardrail.input"))
                    .findFirst()
                    .orElseThrow();

            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RESULT))
                    .isEqualTo("FATAL");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isFalse();
        }
    }

    @Nested
    @DisplayName("Test Case 3: Output guardrail rejecting LLM response")
    class OutputGuardrailRejectingResponseTest {

        @Test
        @DisplayName("Should create output guardrail span before AiService completion with retry indication")
        void shouldCreateOutputGuardrailSpanWithRetryOnRejection() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("Tell me a story");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create output guardrail event that fails with retry
            OutputGuardrailResult.Failure failure = new OutputGuardrailResult.Failure(
                    "Response contains inappropriate content",
                    null,
                    true  // retry=true
            );
            OutputGuardrailResult failedResult = new OutputGuardrailResult(failure, false);

            OutputGuardrailExecutedEvent guardrailEvent = OutputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentFilterOutputGuardrail.class)
                    .result(failedResult)
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Then
            List<SpanData> spans = otelExtension.getSpans();

            // Find the output guardrail span
            SpanData guardrailSpan = spans.stream()
                    .filter(s -> s.getName().equals("guardrail.output.ContentFilterOutputGuardrail"))
                    .findFirst()
                    .orElseThrow();

            // Verify output guardrail span attributes
            assertThat(guardrailSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isFalse();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_TYPE))
                    .isEqualTo("output");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RETRY)).isTrue();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_FAILURE_MESSAGE))
                    .contains("inappropriate content");

            // Verify hierarchical structure - guardrail is child of AiService span
            SpanData aiServiceSpan = spans.stream()
                    .filter(s -> s.getName().equals("AiService.chat"))
                    .findFirst()
                    .orElseThrow();
            assertThat(guardrailSpan.getParentSpanId()).isEqualTo(aiServiceSpan.getSpanId());
        }

        @Test
        @DisplayName("GenAiSpanNames.guardrailOutput should generate correct span names")
        void shouldGenerateCorrectOutputGuardrailSpanNames() {
            assertThat(GenAiSpanNames.guardrailOutput("ContentFilter")).isEqualTo("guardrail.output.ContentFilter");
            assertThat(GenAiSpanNames.guardrailOutput("SafetyGuardrail")).isEqualTo("guardrail.output.SafetyGuardrail");
        }

        @Test
        @DisplayName("Should handle output guardrail with reprompt")
        void shouldHandleOutputGuardrailWithReprompt() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("Give me advice");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create output guardrail that triggers reprompt
            OutputGuardrailResult.Failure failure = new OutputGuardrailResult.Failure(
                    "Response quality too low",
                    null,
                    true,  // retry
                    "Please provide a more helpful response"  // reprompt message
            );
            OutputGuardrailResult failedResult = new OutputGuardrailResult(failure, false);

            OutputGuardrailExecutedEvent guardrailEvent = OutputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentFilterOutputGuardrail.class)
                    .result(failedResult)
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Then
            SpanData guardrailSpan = otelExtension.getSpans().stream()
                    .filter(s -> s.getName().startsWith("guardrail.output"))
                    .findFirst()
                    .orElseThrow();

            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_REPROMPT)).isTrue();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RETRY)).isTrue();
        }

        @Test
        @DisplayName("Should create successful output guardrail span when validation passes")
        void shouldCreateSuccessfulOutputGuardrailSpan() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("Tell me about AI");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // Create passing output guardrail event
            OutputGuardrailExecutedEvent guardrailEvent = OutputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentFilterOutputGuardrail.class)
                    .result(OutputGuardrailResult.success())
                    .build();

            // When
            listener.onEvent(guardrailEvent);

            // Complete AiService
            AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                    .invocationContext(context)
                    .result("AI is a broad field...")
                    .build();
            listener.onEvent(completedEvent);

            // Then
            SpanData guardrailSpan = otelExtension.getSpans().stream()
                    .filter(s -> s.getName().startsWith("guardrail.output"))
                    .findFirst()
                    .orElseThrow();

            assertThat(guardrailSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_PASS)).isTrue();
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_TYPE))
                    .isEqualTo("output");
            assertThat(guardrailSpan.getAttributes().get(GenAiAttributes.GUARDRAIL_RESULT))
                    .isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("Multiple guardrails execution")
    class MultipleGuardrailsTest {

        @Test
        @DisplayName("Should create separate spans for each guardrail execution")
        void shouldCreateSeparateSpansForEachGuardrail() {
            // Given
            UUID invocationId = UUID.randomUUID();
            InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
            UserMessage userMessage = UserMessage.from("Hello");

            // Start AiService span
            AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                    .invocationContext(context)
                    .userMessage(userMessage)
                    .build();
            listener.onEvent(startedEvent);

            // First input guardrail
            InputGuardrailExecutedEvent guardrailEvent1 = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ProhibitedWordsInputGuardrail.class)
                    .result(InputGuardrailResult.success())
                    .build();
            listener.onEvent(guardrailEvent1);

            // Second input guardrail
            InputGuardrailExecutedEvent guardrailEvent2 = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentLengthInputGuardrail.class)
                    .result(InputGuardrailResult.success())
                    .build();
            listener.onEvent(guardrailEvent2);

            // Output guardrail
            OutputGuardrailExecutedEvent outputGuardrailEvent = OutputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ContentFilterOutputGuardrail.class)
                    .result(OutputGuardrailResult.success())
                    .build();
            listener.onEvent(outputGuardrailEvent);

            // Complete AiService
            AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                    .invocationContext(context)
                    .result("Hi there!")
                    .build();
            listener.onEvent(completedEvent);

            // Then
            List<SpanData> spans = otelExtension.getSpans();

            // Should have 4 spans: AiService + 2 input guardrails + 1 output guardrail
            assertThat(spans).hasSize(4);

            // Verify all guardrail spans exist
            assertThat(spans.stream()
                    .filter(s -> s.getName().equals("guardrail.input.ProhibitedWordsInputGuardrail"))
                    .count()).isEqualTo(1);
            assertThat(spans.stream()
                    .filter(s -> s.getName().equals("guardrail.input.ContentLengthInputGuardrail"))
                    .count()).isEqualTo(1);
            assertThat(spans.stream()
                    .filter(s -> s.getName().equals("guardrail.output.ContentFilterOutputGuardrail"))
                    .count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Configuration tests")
    class ConfigurationTest {

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

            InputGuardrailExecutedEvent guardrailEvent = InputGuardrailExecutedEvent.builder()
                    .invocationContext(context)
                    .guardrailClass(ProhibitedWordsInputGuardrail.class)
                    .result(InputGuardrailResult.success())
                    .build();

            int spanCountBefore = otelExtension.getSpans().size();

            // When
            disabledListener.onEvent(guardrailEvent);

            // Then - No new spans should be created
            assertThat(otelExtension.getSpans().size()).isEqualTo(spanCountBefore);
        }
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
            String text = request.userMessage().singleText();
            if (text.toLowerCase().contains("hack")) {
                return InputGuardrailResult.failure("Prohibited word detected: 'hack'");
            }
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
