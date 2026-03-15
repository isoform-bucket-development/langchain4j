package dev.langchain4j.opentelemetry.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.opentelemetry.semconv.GenAiAttributes;
import io.opentelemetry.api.common.AttributesBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for extracting message content from ChatRequest/ChatResponse
 * based on the configured {@link ContentCaptureMode}.
 * <p>
 * This class applies content capture rules:
 * <ul>
 *   <li>{@code FULL}: Captures complete message content (user, system, assistant messages)</li>
 *   <li>{@code METADATA}: Captures message roles and counts, but not actual content</li>
 *   <li>{@code NONE}: Captures only model, tokens, and timing metadata</li>
 * </ul>
 */
public final class ContentCaptureHelper {

    private ContentCaptureHelper() {
        // Utility class
    }

    /**
     * Adds request content attributes based on the capture mode.
     *
     * @param attrBuilder the attributes builder to add to
     * @param request the chat request
     * @param mode the content capture mode
     */
    public static void addRequestAttributes(AttributesBuilder attrBuilder, ChatRequest request, ContentCaptureMode mode) {
        if (mode == null || mode == ContentCaptureMode.NONE) {
            // NONE mode: don't capture any message content or metadata
            return;
        }

        List<ChatMessage> messages = request.messages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        if (mode == ContentCaptureMode.METADATA || mode == ContentCaptureMode.FULL) {
            // Capture message count
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_COUNT, (long) messages.size());

            // Capture message roles
            List<String> roles = extractMessageRoles(messages);
            if (!roles.isEmpty()) {
                attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_MESSAGE_ROLES, roles);
            }
        }

        if (mode == ContentCaptureMode.FULL) {
            // Capture full message content
            addFullRequestContent(attrBuilder, messages);
        }
    }

    /**
     * Adds response content attributes based on the capture mode.
     *
     * @param attrBuilder the attributes builder to add to
     * @param response the chat response
     * @param mode the content capture mode
     */
    public static void addResponseAttributes(AttributesBuilder attrBuilder, ChatResponse response, ContentCaptureMode mode) {
        if (mode == null || mode == ContentCaptureMode.NONE || mode == ContentCaptureMode.METADATA) {
            // NONE and METADATA modes: don't capture response content
            return;
        }

        if (mode == ContentCaptureMode.FULL) {
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage != null && aiMessage.text() != null) {
                attrBuilder.put(GenAiAttributes.GEN_AI_RESPONSE_CONTENT, aiMessage.text());
            }
        }
    }

    /**
     * Extracts message roles from the list of messages.
     */
    private static List<String> extractMessageRoles(List<ChatMessage> messages) {
        List<String> roles = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            ChatMessageType type = message.type();
            if (type != null) {
                roles.add(type.name().toLowerCase());
            }
        }
        return roles;
    }

    /**
     * Adds full request content for FULL capture mode.
     * Captures user and system messages.
     */
    private static void addFullRequestContent(AttributesBuilder attrBuilder, List<ChatMessage> messages) {
        StringBuilder userContent = new StringBuilder();
        StringBuilder systemContent = new StringBuilder();

        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                String text = userMessage.singleText();
                if (text != null) {
                    if (userContent.length() > 0) {
                        userContent.append("\n");
                    }
                    userContent.append(text);
                }
            } else if (message instanceof SystemMessage) {
                SystemMessage systemMessage = (SystemMessage) message;
                String text = systemMessage.text();
                if (text != null) {
                    if (systemContent.length() > 0) {
                        systemContent.append("\n");
                    }
                    systemContent.append(text);
                }
            }
        }

        if (userContent.length() > 0) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_USER_MESSAGE, userContent.toString());
        }

        if (systemContent.length() > 0) {
            attrBuilder.put(GenAiAttributes.GEN_AI_REQUEST_SYSTEM_MESSAGE, systemContent.toString());
        }
    }
}
