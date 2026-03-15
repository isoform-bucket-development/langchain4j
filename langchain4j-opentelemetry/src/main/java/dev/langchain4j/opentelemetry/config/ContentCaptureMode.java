package dev.langchain4j.opentelemetry.config;

/**
 * Enumeration of content capture modes for controlling privacy/visibility tradeoffs
 * in OpenTelemetry instrumentation.
 */
public enum ContentCaptureMode {

    /**
     * Capture complete message content (default).
     * Includes system messages, user messages, and assistant responses.
     */
    FULL,

    /**
     * Capture message roles and counts, but not actual content.
     * Useful for basic observability without exposing sensitive data.
     */
    METADATA,

    /**
     * Capture only model, tokens, and timing metadata.
     * No message content or roles are captured.
     */
    NONE
}
