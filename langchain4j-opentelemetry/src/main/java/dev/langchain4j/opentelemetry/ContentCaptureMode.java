package dev.langchain4j.opentelemetry;

/**
 * Defines the level of content capture for OpenTelemetry tracing.
 *
 * This enum implements REQ-7 (Content Capture Options) and NFR-5 (Security and Privacy)
 * by providing tiered levels of content capture with a privacy-first default.
 */
public enum ContentCaptureMode {
    /**
     * NONE (default): Only metadata (model, tokens, finish_reason) - no message content.
     * This is the privacy-first default mode that complies with GDPR and data protection policies.
     */
    NONE,

    /**
     * METADATA: Include message roles and tool names but no actual content.
     * Provides more context than NONE while still protecting sensitive data.
     */
    METADATA,

    /**
     * FULL: Capture complete messages including user prompts and AI responses.
     * REQUIRES explicit user acknowledgment and should only be used in debugging scenarios.
     * WARNING: This mode may capture PII and sensitive data in traces.
     */
    FULL
}
