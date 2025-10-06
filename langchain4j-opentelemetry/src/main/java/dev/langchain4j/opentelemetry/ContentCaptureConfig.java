package dev.langchain4j.opentelemetry;

import java.util.Set;

/**
 * Configuration for content capture and redaction.
 * By default, all content is redacted for privacy compliance.
 */
public class ContentCaptureConfig {

    private static final String DEFAULT_REDACTION_TEXT = "[REDACTED]";
    private static final int DEFAULT_MAX_LENGTH = 1024;
    private static final Set<String> DEFAULT_REDACTED_FIELDS = Set.of(
        "messages", "prompt", "completion", "content", "input", "output"
    );

    private final boolean enabled;
    private final int maxLength;
    private final Set<String> redactedFields;
    private final String redactionText;

    private ContentCaptureConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.maxLength = builder.maxLength;
        this.redactedFields = Set.copyOf(builder.redactedFields);
        this.redactionText = builder.redactionText;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ContentCaptureConfig defaultConfig() {
        return builder().build();
    }

    public static ContentCaptureConfig disabled() {
        return builder().enabled(false).build();
    }

    public static ContentCaptureConfig enabled(int maxLength) {
        return builder().enabled(true).maxLength(maxLength).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxLength() {
        return maxLength;
    }

    public Set<String> redactedFields() {
        return redactedFields;
    }

    public String redactionText() {
        return redactionText;
    }

    /**
     * Redacts content based on configuration.
     * @param fieldName the field name being processed
     * @param content the content to potentially redact
     * @return redacted or truncated content based on configuration
     */
    public String processContent(String fieldName, String content) {
        if (content == null) {
            return null;
        }

        if (!enabled || redactedFields.contains(fieldName)) {
            return redactionText;
        }

        if (content.length() > maxLength) {
            return content.substring(0, maxLength) + "...";
        }

        return content;
    }

    public static class Builder {
        private boolean enabled = isContentCaptureEnabledByEnvironment();
        private int maxLength = getMaxLengthFromEnvironment();
        private Set<String> redactedFields = DEFAULT_REDACTED_FIELDS;
        private String redactionText = DEFAULT_REDACTION_TEXT;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder redactedFields(Set<String> redactedFields) {
            this.redactedFields = redactedFields;
            return this;
        }

        public Builder redactionText(String redactionText) {
            this.redactionText = redactionText;
            return this;
        }

        public ContentCaptureConfig build() {
            return new ContentCaptureConfig(this);
        }

        private static boolean isContentCaptureEnabledByEnvironment() {
            String enabled = System.getenv("LANGCHAIN4J_OTEL_CONTENT_CAPTURE_ENABLED");
            if (enabled == null) {
                enabled = System.getProperty("langchain4j.opentelemetry.content-capture.enabled");
            }
            // Default to false for privacy-first approach
            return enabled != null && Boolean.parseBoolean(enabled);
        }

        private static int getMaxLengthFromEnvironment() {
            String maxLength = System.getenv("LANGCHAIN4J_OTEL_CONTENT_MAX_LENGTH");
            if (maxLength == null) {
                maxLength = System.getProperty("langchain4j.opentelemetry.content-capture.max-length");
            }
            try {
                return maxLength != null ? Integer.parseInt(maxLength) : DEFAULT_MAX_LENGTH;
            } catch (NumberFormatException e) {
                return DEFAULT_MAX_LENGTH;
            }
        }
    }
}