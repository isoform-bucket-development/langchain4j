package dev.langchain4j.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * OpenTelemetry Semantic Conventions for Generative AI.
 *
 * These attributes follow the experimental GenAI semantic conventions:
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/
 *
 * Since the semconv is still experimental/alpha, we define the attribute keys manually.
 */
public final class GenAiAttributes {

    // System attributes
    public static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");

    // Request attributes
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");
    public static final AttributeKey<Integer> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens").asIntKey();
    public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P = AttributeKey.doubleKey("gen_ai.request.top_p");
    public static final AttributeKey<Integer> GEN_AI_REQUEST_TOP_K = AttributeKey.longKey("gen_ai.request.top_k").asIntKey();

    // Response attributes
    public static final AttributeKey<String> GEN_AI_RESPONSE_ID = AttributeKey.stringKey("gen_ai.response.id");
    public static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS = AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");

    // Usage attributes
    public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    private GenAiAttributes() {
        // Utility class
    }
}
