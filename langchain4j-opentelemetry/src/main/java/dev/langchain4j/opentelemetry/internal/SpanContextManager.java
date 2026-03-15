package dev.langchain4j.opentelemetry.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OpenTelemetry span context for LangChain4j operations.
 * Provides thread-safe context propagation for sync and async operations.
 */
public final class SpanContextManager {

    private static final String SCOPE_KEY = "otel.scope";
    private static final String SPAN_KEY = "otel.span";
    private static final String PARENT_CONTEXT_KEY = "otel.parent.context";

    // Store active spans by invocation ID for hierarchical tracing
    private final Map<UUID, SpanHolder> activeSpans = new ConcurrentHashMap<>();

    private final Tracer tracer;

    public SpanContextManager(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Starts a new span with the given name.
     *
     * @param spanName the name of the span
     * @param invocationId the unique invocation ID for correlation
     * @return the started span
     */
    public Span startSpan(String spanName, UUID invocationId) {
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);

        // Check if there's a parent context to link to
        Context parentContext = Context.current();
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }

        Span span = spanBuilder.startSpan();
        Scope scope = span.makeCurrent();

        activeSpans.put(invocationId, new SpanHolder(span, scope, Context.current()));

        return span;
    }

    /**
     * Gets the current span for an invocation.
     *
     * @param invocationId the invocation ID
     * @return the span, or null if not found
     */
    public Span getSpan(UUID invocationId) {
        SpanHolder holder = activeSpans.get(invocationId);
        return holder != null ? holder.span : null;
    }

    /**
     * Gets the context for an invocation.
     *
     * @param invocationId the invocation ID
     * @return the context, or the current context if not found
     */
    public Context getContext(UUID invocationId) {
        SpanHolder holder = activeSpans.get(invocationId);
        return holder != null ? holder.context : Context.current();
    }

    /**
     * Ends a span with the given status.
     *
     * @param invocationId the invocation ID
     * @param statusCode the status code
     */
    public void endSpan(UUID invocationId, StatusCode statusCode) {
        endSpan(invocationId, statusCode, null);
    }

    /**
     * Ends a span with the given status and optional description.
     *
     * @param invocationId the invocation ID
     * @param statusCode the status code
     * @param description optional description for the status
     */
    public void endSpan(UUID invocationId, StatusCode statusCode, String description) {
        SpanHolder holder = activeSpans.remove(invocationId);
        if (holder != null) {
            if (description != null) {
                holder.span.setStatus(statusCode, description);
            } else {
                holder.span.setStatus(statusCode);
            }
            holder.scope.close();
            holder.span.end();
        }
    }

    /**
     * Records an exception on the span.
     *
     * @param invocationId the invocation ID
     * @param exception the exception to record
     */
    public void recordException(UUID invocationId, Throwable exception) {
        SpanHolder holder = activeSpans.get(invocationId);
        if (holder != null) {
            holder.span.recordException(exception);
        }
    }

    /**
     * Gets the current context.
     *
     * @return the current context
     */
    public Context getCurrentContext() {
        return Context.current();
    }

    /**
     * Runs a runnable with the given context.
     *
     * @param context the context to use
     * @param runnable the runnable to execute
     */
    public void runWithContext(Context context, Runnable runnable) {
        try (Scope ignored = context.makeCurrent()) {
            runnable.run();
        }
    }

    /**
     * Stores context in attributes map for cross-listener communication.
     *
     * @param attributes the attributes map
     * @param invocationId the invocation ID
     */
    public void storeContextInAttributes(Map<Object, Object> attributes, UUID invocationId) {
        SpanHolder holder = activeSpans.get(invocationId);
        if (holder != null) {
            attributes.put(SPAN_KEY + "." + invocationId, holder.span);
            attributes.put(PARENT_CONTEXT_KEY + "." + invocationId, holder.context);
        }
    }

    /**
     * Retrieves the parent context from attributes.
     *
     * @param attributes the attributes map
     * @param invocationId the invocation ID
     * @return the parent context, or null if not found
     */
    public Context getParentContextFromAttributes(Map<Object, Object> attributes, UUID invocationId) {
        Object context = attributes.get(PARENT_CONTEXT_KEY + "." + invocationId);
        return context instanceof Context ? (Context) context : null;
    }

    private static class SpanHolder {
        final Span span;
        final Scope scope;
        final Context context;

        SpanHolder(Span span, Scope scope, Context context) {
            this.span = span;
            this.scope = scope;
            this.context = context;
        }
    }
}
