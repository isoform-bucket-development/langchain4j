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
     * The operation name (e.g., "chat", "completion").
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
}
