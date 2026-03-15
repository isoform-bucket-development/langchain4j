package dev.langchain4j.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

/**
 * OpenTelemetry Semantic Convention attribute keys for Generative AI operations.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI Semantic Conventions</a>
 */
public final class GenAiAttributes {

    private GenAiAttributes() {}

    /**
     * The name of the GenAI system (e.g., "openai", "anthropic", "ollama").
     */
    public static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");

    /**
     * The name of the model used for the request.
     */
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");

    /**
     * The temperature parameter for the request.
     */
    public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");

    /**
     * The top_p parameter for the request.
     */
    public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P = AttributeKey.doubleKey("gen_ai.request.top_p");

    /**
     * The maximum number of tokens to generate.
     */
    public static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");

    /**
     * The unique identifier for the response.
     */
    public static final AttributeKey<String> GEN_AI_RESPONSE_ID = AttributeKey.stringKey("gen_ai.response.id");

    /**
     * The finish reasons for the response.
     */
    public static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

    /**
     * The number of input tokens used.
     */
    public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");

    /**
     * The number of output tokens generated.
     */
    public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    /**
     * The total number of tokens used.
     */
    public static final AttributeKey<Long> GEN_AI_USAGE_TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

    /**
     * The operation name (e.g., "chat", "completion", "aiservice").
     */
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    /**
     * Whether this is a streaming operation.
     */
    public static final AttributeKey<Boolean> GEN_AI_REQUEST_STREAMING = AttributeKey.booleanKey("gen_ai.request.streaming");

    /**
     * Error type for failed operations.
     */
    public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    // AiService-specific attributes

    /**
     * The fully-qualified name of the AiService interface.
     */
    public static final AttributeKey<String> AISERVICE_INTERFACE_NAME = AttributeKey.stringKey("aiservice.interface.name");

    /**
     * The name of the method being invoked on the AiService.
     */
    public static final AttributeKey<String> AISERVICE_METHOD_NAME = AttributeKey.stringKey("aiservice.method.name");

    /**
     * The unique identifier for the AiService invocation.
     */
    public static final AttributeKey<String> AISERVICE_INVOCATION_ID = AttributeKey.stringKey("aiservice.invocation.id");

    // Content capture attributes

    /**
     * The prompt content sent to the model.
     */
    public static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");

    /**
     * The completion/response content from the model.
     */
    public static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");

    /**
     * The system message content.
     */
    public static final AttributeKey<String> GEN_AI_SYSTEM_MESSAGE = AttributeKey.stringKey("gen_ai.system.message");

    /**
     * The user message content.
     */
    public static final AttributeKey<String> GEN_AI_USER_MESSAGE = AttributeKey.stringKey("gen_ai.user.message");

    // Guardrail-specific attributes

    /**
     * The name of the guardrail class that performed the validation.
     */
    public static final AttributeKey<String> GUARDRAIL_NAME = AttributeKey.stringKey("guardrail.name");

    /**
     * The type of guardrail (input or output).
     */
    public static final AttributeKey<String> GUARDRAIL_TYPE = AttributeKey.stringKey("guardrail.type");

    /**
     * Whether the guardrail validation passed (true) or failed (false).
     */
    public static final AttributeKey<Boolean> GUARDRAIL_PASS = AttributeKey.booleanKey("guardrail.pass");

    /**
     * The result of the guardrail validation (SUCCESS, FAILURE, FATAL, SUCCESS_WITH_RESULT).
     */
    public static final AttributeKey<String> GUARDRAIL_RESULT = AttributeKey.stringKey("guardrail.result");

    /**
     * The failure message if the guardrail validation failed.
     */
    public static final AttributeKey<String> GUARDRAIL_FAILURE_MESSAGE = AttributeKey.stringKey("guardrail.failure.message");

    /**
     * Whether the guardrail triggered a retry.
     */
    public static final AttributeKey<Boolean> GUARDRAIL_RETRY = AttributeKey.booleanKey("guardrail.retry");

    /**
     * Whether the guardrail triggered a reprompt.
     */
    public static final AttributeKey<Boolean> GUARDRAIL_REPROMPT = AttributeKey.booleanKey("guardrail.reprompt");

    // Tool execution attributes

    /**
     * The name of the tool being executed.
     */
    public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("tool.name");

    /**
     * The unique identifier of the tool execution request.
     */
    public static final AttributeKey<String> TOOL_EXECUTION_ID = AttributeKey.stringKey("tool.execution.id");

    /**
     * The arguments passed to the tool (JSON string).
     */
    public static final AttributeKey<String> TOOL_ARGUMENTS = AttributeKey.stringKey("tool.arguments");

    /**
     * The result of the tool execution.
     */
    public static final AttributeKey<String> TOOL_RESULT = AttributeKey.stringKey("tool.result");
}
