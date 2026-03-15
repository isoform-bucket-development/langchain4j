package dev.langchain4j.opentelemetry.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes sensitive content before recording in OpenTelemetry spans.
 *
 * <p>This class provides utilities to detect and redact sensitive information such as:
 * <ul>
 *   <li>API keys (OpenAI sk-*, AWS AKIA*, Azure keys, etc.)</li>
 *   <li>Bearer tokens and JWT tokens</li>
 *   <li>Password fields</li>
 *   <li>Generic secrets and credentials</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ContentSanitizer sanitizer = ContentSanitizer.getInstance();
 * String safeContent = sanitizer.sanitize("My API key is sk-test-abc123xyz");
 * // Result: "My API key is [REDACTED]"
 * }</pre>
 *
 * <p>This class is thread-safe and designed for concurrent use.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OTel GenAI Semantic Conventions</a>
 */
public final class ContentSanitizer {

    private static final ContentSanitizer INSTANCE = new ContentSanitizer();

    /**
     * The redaction placeholder used to replace sensitive content.
     */
    public static final String REDACTED = "[REDACTED]";

    // OpenAI API key pattern: sk-[alphanumeric] or sk-proj-[alphanumeric]
    private static final Pattern OPENAI_API_KEY = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]{20,}\\b"
    );

    // AWS Access Key ID pattern: AKIA followed by 16 alphanumeric characters
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
            "\\bAKIA[A-Z0-9]{16}\\b"
    );

    // AWS Secret Access Key pattern (40 character base64-like string)
    private static final Pattern AWS_SECRET_KEY = Pattern.compile(
            "\\b[A-Za-z0-9/+=]{40}(?=\\s|$|[\"'}])"
    );

    // Azure API key pattern (32 character hex string)
    private static final Pattern AZURE_API_KEY = Pattern.compile(
            "\\b[a-f0-9]{32}\\b"
    );

    // Generic API key patterns (various formats)
    private static final Pattern GENERIC_API_KEY = Pattern.compile(
            "\\b(?:api[_-]?key|apikey|access[_-]?token|auth[_-]?token|secret[_-]?key|private[_-]?key)[\"'\\s:=]+[\"']?([A-Za-z0-9_\\-+/=]{16,})[\"']?",
            Pattern.CASE_INSENSITIVE
    );

    // Bearer token pattern
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "\\bBearer\\s+[A-Za-z0-9_\\-+/.=]{10,}\\b",
            Pattern.CASE_INSENSITIVE
    );

    // JWT token pattern (three base64 segments separated by dots)
    private static final Pattern JWT_TOKEN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*\\b"
    );

    // Password field pattern in JSON/text
    private static final Pattern PASSWORD_FIELD = Pattern.compile(
            "(?:\"password\"\\s*:\\s*\"[^\"]*\"|'password'\\s*:\\s*'[^']*'|password[\\s=:]+\\S+)",
            Pattern.CASE_INSENSITIVE
    );

    // Environment variable reference with secrets
    private static final Pattern ENV_VAR_SECRET = Pattern.compile(
            "\\$\\{?(?:API_KEY|SECRET|PASSWORD|TOKEN|CREDENTIAL|AUTH)[A-Z0-9_]*\\}?",
            Pattern.CASE_INSENSITIVE
    );

    // Anthropic API key pattern: sk-ant-[alphanumeric]
    private static final Pattern ANTHROPIC_API_KEY = Pattern.compile(
            "\\bsk-ant-[A-Za-z0-9_-]{20,}\\b"
    );

    // Google Cloud API key pattern
    private static final Pattern GOOGLE_API_KEY = Pattern.compile(
            "\\bAIza[A-Za-z0-9_-]{35}\\b"
    );

    // Hugging Face API token pattern
    private static final Pattern HUGGINGFACE_TOKEN = Pattern.compile(
            "\\bhf_[A-Za-z0-9]{30,}\\b"
    );

    // Generic long secret pattern (common in URLs or configs)
    private static final Pattern GENERIC_SECRET_IN_URL = Pattern.compile(
            "(?:key|token|secret|password|auth|credential)=([A-Za-z0-9_\\-+/=]{16,})(?:&|$|\\s)",
            Pattern.CASE_INSENSITIVE
    );

    // All patterns for quick credential detection
    private static final Pattern[] CREDENTIAL_PATTERNS = {
            OPENAI_API_KEY,
            AWS_ACCESS_KEY,
            ANTHROPIC_API_KEY,
            GOOGLE_API_KEY,
            HUGGINGFACE_TOKEN,
            BEARER_TOKEN,
            JWT_TOKEN
    };

    private ContentSanitizer() {
        // Private constructor for singleton
    }

    /**
     * Returns the singleton instance of ContentSanitizer.
     *
     * @return the singleton ContentSanitizer instance
     */
    public static ContentSanitizer getInstance() {
        return INSTANCE;
    }

    /**
     * Sanitizes the content by removing all detected sensitive information.
     * This method applies all known credential patterns and replaces matches with [REDACTED].
     *
     * @param content the content to sanitize (may be null)
     * @return the sanitized content, or null if input was null
     */
    public String sanitize(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return redactCredentials(content);
    }

    /**
     * Redacts credential-like patterns from the given text.
     * This is the primary sanitization method that handles all credential patterns.
     *
     * @param text the text to redact credentials from (may be null)
     * @return the text with credentials redacted, or null if input was null
     */
    public String redactCredentials(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Redact OpenAI API keys
        result = OPENAI_API_KEY.matcher(result).replaceAll(REDACTED);

        // Redact Anthropic API keys
        result = ANTHROPIC_API_KEY.matcher(result).replaceAll(REDACTED);

        // Redact AWS access keys
        result = AWS_ACCESS_KEY.matcher(result).replaceAll(REDACTED);

        // Redact AWS secret keys
        result = AWS_SECRET_KEY.matcher(result).replaceAll(REDACTED);

        // Redact Azure API keys
        result = AZURE_API_KEY.matcher(result).replaceAll(REDACTED);

        // Redact Google API keys
        result = GOOGLE_API_KEY.matcher(result).replaceAll(REDACTED);

        // Redact Hugging Face tokens
        result = HUGGINGFACE_TOKEN.matcher(result).replaceAll(REDACTED);

        // Redact Bearer tokens
        result = BEARER_TOKEN.matcher(result).replaceAll("Bearer " + REDACTED);

        // Redact JWT tokens
        result = JWT_TOKEN.matcher(result).replaceAll(REDACTED);

        // Redact password fields
        result = redactPasswordFields(result);

        // Redact generic API key patterns
        result = redactGenericApiKeys(result);

        // Redact secrets in URLs/query strings
        result = redactSecretsInUrls(result);

        // Redact environment variable references to secrets
        result = ENV_VAR_SECRET.matcher(result).replaceAll(REDACTED);

        return result;
    }

    /**
     * Checks if a value looks like a credential (API key, token, etc.).
     * This method performs a quick check without modifying the input.
     *
     * @param value the value to check (may be null)
     * @return true if the value appears to be a credential, false otherwise
     */
    public boolean isCredentialLike(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Check against all known credential patterns
        for (Pattern pattern : CREDENTIAL_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }

        // Check for generic patterns
        if (GENERIC_API_KEY.matcher(value).find()) {
            return true;
        }

        // Check for password fields
        if (PASSWORD_FIELD.matcher(value).find()) {
            return true;
        }

        // Check for environment variable secrets
        if (ENV_VAR_SECRET.matcher(value).find()) {
            return true;
        }

        // Check for secrets in URLs
        if (GENERIC_SECRET_IN_URL.matcher(value).find()) {
            return true;
        }

        return false;
    }

    /**
     * Redacts password field values while preserving the field structure.
     */
    private String redactPasswordFields(String text) {
        Matcher matcher = PASSWORD_FIELD.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String match = matcher.group();
            String replacement;
            if (match.contains("\"")) {
                replacement = "\"password\": \"" + REDACTED + "\"";
            } else if (match.contains("'")) {
                replacement = "'password': '" + REDACTED + "'";
            } else {
                replacement = "password=" + REDACTED;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Redacts generic API key patterns while preserving field names.
     */
    private String redactGenericApiKeys(String text) {
        Matcher matcher = GENERIC_API_KEY.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fullMatch = matcher.group();
            // Find the position of the actual key value and replace only that part
            int keyStart = fullMatch.indexOf(matcher.group(1));
            if (keyStart >= 0) {
                String prefix = fullMatch.substring(0, keyStart);
                String suffix = fullMatch.substring(keyStart + matcher.group(1).length());
                String replacement = prefix + REDACTED + suffix;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Redacts secrets found in URL query parameters.
     */
    private String redactSecretsInUrls(String text) {
        Matcher matcher = GENERIC_SECRET_IN_URL.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fullMatch = matcher.group();
            String secretValue = matcher.group(1);
            String replacement = fullMatch.replace(secretValue, REDACTED);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Sanitizes error messages that may contain sensitive information.
     * This is particularly important for API errors that might echo back credentials.
     *
     * @param errorMessage the error message to sanitize (may be null)
     * @return the sanitized error message, or null if input was null
     */
    public String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return errorMessage;
        }

        // Apply standard credential redaction
        String sanitized = redactCredentials(errorMessage);

        // Additional check for API key echoed in error context
        // Pattern: "Invalid API key: sk-..." or "Authentication failed for: sk-..."
        sanitized = sanitized.replaceAll(
                "(?i)(invalid|authentication|unauthorized|error)([^:]*:?\\s*)(['\"]?)([A-Za-z0-9_-]{20,})(['\"]?)",
                "$1$2$3" + REDACTED + "$5"
        );

        return sanitized;
    }

    /**
     * Sanitizes tool arguments JSON, redacting any sensitive field values.
     *
     * @param arguments the JSON arguments string (may be null)
     * @return the sanitized arguments, or null if input was null
     */
    public String sanitizeToolArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return arguments;
        }

        // First apply general credential redaction
        String sanitized = redactCredentials(arguments);

        // Then look for common sensitive field names in JSON and redact their values
        sanitized = redactJsonSensitiveFields(sanitized);

        return sanitized;
    }

    /**
     * Redacts values of known sensitive field names in JSON content.
     */
    private String redactJsonSensitiveFields(String json) {
        // Pattern to match JSON key-value pairs with sensitive field names
        Pattern sensitiveJsonField = Pattern.compile(
                "\"(api[_-]?key|apikey|secret|password|token|credential|auth|private[_-]?key|access[_-]?key)\"\\s*:\\s*\"([^\"]*)\"|" +
                "'(api[_-]?key|apikey|secret|password|token|credential|auth|private[_-]?key|access[_-]?key)'\\s*:\\s*'([^']*)'",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = sensitiveJsonField.matcher(json);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String match = matcher.group();
            String replacement;
            if (match.contains("\"")) {
                String fieldName = matcher.group(1);
                replacement = "\"" + fieldName + "\": \"" + REDACTED + "\"";
            } else {
                String fieldName = matcher.group(3);
                replacement = "'" + fieldName + "': '" + REDACTED + "'";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
