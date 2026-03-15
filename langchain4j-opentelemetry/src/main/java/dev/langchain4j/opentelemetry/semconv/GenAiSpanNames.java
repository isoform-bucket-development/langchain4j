package dev.langchain4j.opentelemetry.semconv;

/**
 * Span naming conventions for GenAI operations following semantic conventions.
 */
public final class GenAiSpanNames {

    private GenAiSpanNames() {}

    /**
     * Span name for chat operations.
     */
    public static final String CHAT = "gen_ai.chat";

    /**
     * Span name for content completion operations.
     */
    public static final String CONTENT_COMPLETION = "gen_ai.content.completion";

    /**
     * Creates a span name for tool execution.
     *
     * @param toolName the name of the tool
     * @return the span name
     */
    public static String toolExecution(String toolName) {
        return "tool.execution." + toolName;
    }

    /**
     * Creates a span name for input guardrail.
     *
     * @param name the guardrail name
     * @return the span name
     */
    public static String guardrailInput(String name) {
        return "guardrail.input." + name;
    }

    /**
     * Creates a span name for output guardrail.
     *
     * @param name the guardrail name
     * @return the span name
     */
    public static String guardrailOutput(String name) {
        return "guardrail.output." + name;
    }

    /**
     * Creates a span name for AI service method.
     *
     * @param methodName the method name
     * @return the span name
     */
    public static String aiServiceMethod(String methodName) {
        return "AiService." + methodName;
    }
}
