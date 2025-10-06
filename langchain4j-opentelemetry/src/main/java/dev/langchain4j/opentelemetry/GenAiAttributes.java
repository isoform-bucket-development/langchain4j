package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OpenTelemetry semantic convention attributes for Generative AI operations.
 * Based on the OpenTelemetry Generative AI Semantic Conventions.
 */
public final class GenAiAttributes {

    // System attributes
    public static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");

    // Request attributes
    public static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P = AttributeKey.doubleKey("gen_ai.request.top_p");
    public static final AttributeKey<Long> GEN_AI_REQUEST_TOP_K = AttributeKey.longKey("gen_ai.request.top_k");
    public static final AttributeKey<String> GEN_AI_REQUEST_FREQUENCY_PENALTY = AttributeKey.stringKey("gen_ai.request.frequency_penalty");
    public static final AttributeKey<String> GEN_AI_REQUEST_PRESENCE_PENALTY = AttributeKey.stringKey("gen_ai.request.presence_penalty");
    public static final AttributeKey<String> GEN_AI_REQUEST_STOP_SEQUENCES = AttributeKey.stringKey("gen_ai.request.stop_sequences");

    // Response attributes
    public static final AttributeKey<String> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringKey("gen_ai.response.finish_reasons");
    public static final AttributeKey<String> GEN_AI_RESPONSE_ID = AttributeKey.stringKey("gen_ai.response.id");

    // Usage attributes
    public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    // Content attributes (optional, based on content capture config)
    public static final AttributeKey<String> GEN_AI_PROMPT = AttributeKey.stringKey("gen_ai.prompt");
    public static final AttributeKey<String> GEN_AI_COMPLETION = AttributeKey.stringKey("gen_ai.completion");

    // LangChain4j specific attributes
    public static final AttributeKey<String> AI_SERVICE_INTERFACE = AttributeKey.stringKey("ai.service.interface");
    public static final AttributeKey<String> AI_SERVICE_METHOD = AttributeKey.stringKey("ai.service.method");
    public static final AttributeKey<String> AI_SERVICE_MEMORY_ID = AttributeKey.stringKey("ai.service.memory_id");

    // Tool attributes
    public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("tool.name");
    public static final AttributeKey<String> TOOL_PARAMETERS = AttributeKey.stringKey("tool.parameters");
    public static final AttributeKey<String> TOOL_RESULT = AttributeKey.stringKey("tool.result");
    public static final AttributeKey<Long> TOOL_DURATION = AttributeKey.longKey("tool.duration");

    // Guardrail attributes
    public static final AttributeKey<String> GUARDRAIL_TYPE = AttributeKey.stringKey("guardrail.type");
    public static final AttributeKey<String> GUARDRAIL_NAME = AttributeKey.stringKey("guardrail.name");
    public static final AttributeKey<String> GUARDRAIL_RESULT = AttributeKey.stringKey("guardrail.result");
    public static final AttributeKey<String> GUARDRAIL_REASON = AttributeKey.stringKey("guardrail.reason");

    // Error attributes
    public static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    private GenAiAttributes() {
        // Utility class
    }
}