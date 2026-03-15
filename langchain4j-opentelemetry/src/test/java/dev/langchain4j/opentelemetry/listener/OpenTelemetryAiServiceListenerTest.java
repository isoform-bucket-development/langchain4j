package dev.langchain4j.opentelemetry.listener;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.opentelemetry.config.ContentCaptureMode;
import dev.langchain4j.opentelemetry.config.OpenTelemetryLangChain4jConfig;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import dev.langchain4j.opentelemetry.semconv.GenAiSpanNames;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTelemetryAiServiceListener}.
 *
 * Verifies that the listener correctly generates OpenTelemetry spans
 * for AiService method invocations with proper hierarchical structure.
 */
class OpenTelemetryAiServiceListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    @Test
    @DisplayName("Should create span named 'AiService.chat' for chat method invocation")
    void shouldCreateSpanWithCorrectName() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
        UserMessage userMessage = UserMessage.from("Hello, how are you?");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("I'm doing well, thank you!")
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("AiService.chat");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        // Verify attributes
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_METHOD_NAME))
                .isEqualTo("chat");
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_INTERFACE_NAME))
                .isEqualTo("dev.langchain4j.service.Assistant");
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_INVOCATION_ID))
                .isEqualTo(invocationId.toString());
    }

    @Test
    @DisplayName("Should capture user message content when content capture is enabled")
    void shouldCaptureUserMessageContent() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "chat");
        String userMessageText = "What is the weather today?";
        UserMessage userMessage = UserMessage.from(userMessageText);

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("The weather is sunny!")
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.chat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USER_MESSAGE))
                .isEqualTo(userMessageText);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_COMPLETION))
                .isEqualTo("The weather is sunny!");
    }

    @Test
    @DisplayName("Should capture system message in span attributes")
    void shouldCaptureSystemMessageWhenContentCaptureEnabled() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "TranslatorService", "translate");
        String systemMessageText = "You are a professional translator. Translate the following text accurately.";
        SystemMessage systemMessage = SystemMessage.from(systemMessageText);
        UserMessage userMessage = UserMessage.from("Hello world");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .systemMessage(systemMessage)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Bonjour le monde")
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.translate"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM_MESSAGE))
                .isEqualTo(systemMessageText);
    }

    @Test
    @DisplayName("Should not capture system message when content capture is disabled")
    void shouldNotCaptureSystemMessageWhenContentCaptureDisabled() {
        // Given
        OpenTelemetryAiServiceListener metadataOnlyListener = OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .contentCaptureMode(ContentCaptureMode.METADATA)
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "TranslatorService", "translate2");
        SystemMessage systemMessage = SystemMessage.from("You are a translator");
        UserMessage userMessage = UserMessage.from("Hello");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .systemMessage(systemMessage)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Bonjour")
                .build();

        // When
        metadataOnlyListener.getStartedListener().onEvent(startedEvent);
        metadataOnlyListener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.translate2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM_MESSAGE)).isNull();
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USER_MESSAGE)).isNull();
        // But should still have method name
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_METHOD_NAME))
                .isEqualTo("translate2");
    }

    @Test
    @DisplayName("Should complete span after JSON extraction with result")
    void shouldCompleteSpanWithPojoResult() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "PersonExtractor", "extractPerson");
        UserMessage userMessage = UserMessage.from("John Doe is 30 years old and works as an engineer");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        // Simulate a complex POJO result
        Person extractedPerson = new Person("John Doe", 30, "engineer");

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result(extractedPerson)
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.extractPerson"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        // Result should be captured as string representation
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_COMPLETION))
                .contains("John Doe")
                .contains("30")
                .contains("engineer");
    }

    @Test
    @DisplayName("Should handle null result gracefully")
    void shouldHandleNullResult() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "VoidService", "process");
        UserMessage userMessage = UserMessage.from("Process this");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result(null)
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.process"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_COMPLETION)).isNull();
    }

    @Test
    @DisplayName("Should record error status when AiService method fails")
    void shouldRecordErrorStatus() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "FailingService", "fail");
        UserMessage userMessage = UserMessage.from("This will fail");
        RuntimeException error = new RuntimeException("LLM API error: rate limit exceeded");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        AiServiceErrorEvent errorEvent = AiServiceErrorEvent.builder()
                .invocationContext(context)
                .error(error)
                .build();

        // When
        listener.getStartedListener().onEvent(startedEvent);
        listener.getErrorListener().onEvent(errorEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData span = spans.stream()
                .filter(s -> s.getName().equals("AiService.fail"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).contains("rate limit exceeded");

        // Should have recorded the exception
        assertThat(span.getEvents()).isNotEmpty();
        assertThat(span.getEvents().get(0).getName()).isEqualTo("exception");
    }

    @Test
    @DisplayName("Should create AiService span that can be parent of ChatModel span")
    void shouldSupportHierarchicalSpans() {
        // Given
        OpenTelemetryAiServiceListener listener = createListener();
        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "hierarchicalChat");
        UserMessage userMessage = UserMessage.from("Hello");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        // When - Start the AiService span
        listener.getStartedListener().onEvent(startedEvent);

        // Verify the span context is available for child spans
        assertThat(listener.getSpanContextManager().getSpan(invocationId)).isNotNull();
        assertThat(listener.getSpanContextManager().getContext(invocationId)).isNotNull();

        // Complete the AiService span
        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Hi there!")
                .build();
        listener.getCompletedListener().onEvent(completedEvent);

        // Then
        List<SpanData> spans = otelExtension.getSpans();
        SpanData aiServiceSpan = spans.stream()
                .filter(s -> s.getName().equals("AiService.hierarchicalChat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        // Verify the span has a valid trace ID that can be used for parenting
        assertThat(aiServiceSpan.getTraceId()).isNotNull();
        assertThat(aiServiceSpan.getSpanId()).isNotNull();
    }

    @Test
    @DisplayName("Should not create spans when tracing is disabled")
    void shouldNotCreateSpansWhenTracingDisabled() {
        // Given
        OpenTelemetryLangChain4jConfig config = OpenTelemetryLangChain4jConfig.builder()
                .tracingEnabled(false)
                .build();

        OpenTelemetryAiServiceListener disabledListener = OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .config(config)
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "Assistant", "disabledChat");
        UserMessage userMessage = UserMessage.from("Hello");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Hi")
                .build();

        int spanCountBefore = otelExtension.getSpans().size();

        // When
        disabledListener.getStartedListener().onEvent(startedEvent);
        disabledListener.getCompletedListener().onEvent(completedEvent);

        // Then - No new spans should be created
        assertThat(otelExtension.getSpans().size()).isEqualTo(spanCountBefore);
    }

    @Test
    @DisplayName("Should use ContentCaptureMode.NONE correctly")
    void shouldUseContentCaptureModeNone() {
        // Given
        OpenTelemetryAiServiceListener noCaptureListener = OpenTelemetryAiServiceListener.builder()
                .openTelemetry(otelExtension.getOpenTelemetry())
                .contentCaptureMode(ContentCaptureMode.NONE)
                .build();

        UUID invocationId = UUID.randomUUID();
        InvocationContext context = createInvocationContext(invocationId, "SecretService", "processSecret");
        SystemMessage systemMessage = SystemMessage.from("Secret instructions");
        UserMessage userMessage = UserMessage.from("Secret message");

        AiServiceStartedEvent startedEvent = AiServiceStartedEvent.builder()
                .invocationContext(context)
                .systemMessage(systemMessage)
                .userMessage(userMessage)
                .build();

        AiServiceCompletedEvent completedEvent = AiServiceCompletedEvent.builder()
                .invocationContext(context)
                .result("Secret result")
                .build();

        // When
        noCaptureListener.getStartedListener().onEvent(startedEvent);
        noCaptureListener.getCompletedListener().onEvent(completedEvent);

        // Then
        SpanData span = otelExtension.getSpans().stream()
                .filter(s -> s.getName().equals("AiService.processSecret"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found"));

        // Content should not be captured
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM_MESSAGE)).isNull();
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USER_MESSAGE)).isNull();
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_COMPLETION)).isNull();

        // But metadata should still be present
        assertThat(span.getAttributes().get(GenAiAttributes.AISERVICE_METHOD_NAME))
                .isEqualTo("processSecret");
    }

    @Test
    @DisplayName("GenAiSpanNames.aiServiceMethod should generate correct span names")
    void shouldGenerateCorrectSpanNames() {
        assertThat(GenAiSpanNames.aiServiceMethod("chat")).isEqualTo("AiService.chat");
        assertThat(GenAiSpanNames.aiServiceMethod("translate")).isEqualTo("AiService.translate");
        assertThat(GenAiSpanNames.aiServiceMethod("extractPerson")).isEqualTo("AiService.extractPerson");
    }

    @Test
    @DisplayName("GenAiSpanNames constants should match semantic conventions")
    void shouldHaveCorrectConstants() {
        assertThat(GenAiSpanNames.CHAT).isEqualTo("gen_ai.chat");
        assertThat(GenAiSpanNames.AISERVICE_PREFIX).isEqualTo("AiService.");
    }

    @Test
    @DisplayName("Should return all three listeners")
    void shouldReturnAllThreeListeners() {
        OpenTelemetryAiServiceListener listener = createListener();
        assertThat(listener.getAllListeners()).hasSize(3);
        assertThat(listener.getAllListeners()).contains(
                listener.getStartedListener(),
                listener.getCompletedListener(),
                listener.getErrorListener()
        );
    }

    // Helper methods
    private OpenTelemetryAiServiceListener createListener() {
        return OpenTelemetryAiServiceListener.builder()
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

    // Test POJO for complex return type test
    static class Person {
        private final String name;
        private final int age;
        private final String occupation;

        Person(String name, int age, String occupation) {
            this.name = name;
            this.age = age;
            this.occupation = occupation;
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + ", occupation='" + occupation + "'}";
        }
    }
}
