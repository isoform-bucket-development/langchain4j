# LangChain4j OpenTelemetry Integration

[![Maven Central](https://img.shields.io/maven-central/v/dev.langchain4j/langchain4j-opentelemetry)](https://maven-badges.herokuapp.com/maven-central/dev.langchain4j/langchain4j-opentelemetry)

## Overview

Provides native OpenTelemetry instrumentation for LangChain4j applications, enabling:

- **Automatic Tracing**: All LLM requests, AI Service invocations, and tool executions generate OTel spans
- **Metrics Collection**: Token usage, request duration, and error rates following GenAI semantic conventions
- **Context Propagation**: W3C Trace Context support for distributed tracing
- **Privacy Controls**: Configurable message content capture (default: metadata only)
- **Semantic Conventions**: Compliant with [OpenTelemetry Semantic Conventions for Generative AI](https://opentelemetry.io/docs/specs/semconv/gen-ai/)

## Status

**TDD Red Phase**: This module contains comprehensive test scaffolding but no implementations yet. All tests are expected to FAIL until the following classes are implemented:

- `OpenTelemetryChatModelListener`
- `OpenTelemetryAiServiceStartedListener`
- `OpenTelemetryAiServiceCompletedListener`
- `OpenTelemetryAiServiceErrorListener`
- `OpenTelemetryToolExecutedListener`
- `OpenTelemetryConfig`
- `ContentCaptureMode`

## Installation

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-opentelemetry</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

## Usage (Planned)

### Basic Setup

```java
// Configure OpenTelemetry SDK
OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .setMeterProvider(meterProvider)
    .build();

// Create OpenTelemetry listeners
OpenTelemetryChatModelListener chatListener = new OpenTelemetryChatModelListener(
    openTelemetry.getTracer("langchain4j"),
    openTelemetry.getMeter("langchain4j"),
    OpenTelemetryConfig.builder().build()
);

// Use with ChatModel
ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o-mini")
    .listeners(List.of(chatListener))
    .build();
```

### Privacy-Safe Configuration (Default)

```java
// Default: Only metadata captured (no message content)
OpenTelemetryConfig config = OpenTelemetryConfig.builder()
    .contentCaptureMode(ContentCaptureMode.NONE)
    .build();
```

### Full Content Capture (Opt-in)

```java
// ⚠️ Warning: Captures full message content - ensure compliance with privacy policies
OpenTelemetryConfig config = OpenTelemetryConfig.builder()
    .contentCaptureMode(ContentCaptureMode.FULL)
    .build();
```

### Sampling for High-Volume Production

```java
// Sample 10% of requests to reduce overhead
OpenTelemetryConfig config = OpenTelemetryConfig.builder()
    .samplingRate(0.1)
    .build();
```

## Test Coverage

This module includes comprehensive TDD test scaffolding covering:

1. **ChatModel Tracing** (`OpenTelemetryChatModelListenerTest`)
   - Automatic span generation
   - Semantic convention compliance
   - Error handling
   - Provider inference
   - Streaming support
   - Context propagation

2. **Metrics Collection** (`OpenTelemetryMetricsTest`)
   - Token usage metrics
   - Duration histograms
   - Error rate counters
   - Metric dimensions (model, provider)
   - Aggregation across requests

3. **AiService Integration** (`OpenTelemetryAiServiceListenerTest`)
   - AI Service invocation spans
   - Tool execution tracing
   - Guardrail execution events
   - Hierarchical span structure
   - Error tracking

4. **Configuration** (`OpenTelemetryConfigTest`)
   - Default values
   - Independent enable/disable
   - Sampling rates
   - Content capture modes
   - Attribute filtering
   - Custom attributes

## Running Tests

Tests are expected to FAIL until implementations are complete:

```bash
mvn test -pl langchain4j-opentelemetry
```

## Semantic Conventions

Follows OpenTelemetry Semantic Conventions for GenAI:

### Span Attributes

- `gen_ai.system`: LLM provider (e.g., "openai", "anthropic")
- `gen_ai.request.model`: Model name (e.g., "gpt-4o-mini")
- `gen_ai.request.temperature`, `gen_ai.request.top_p`, `gen_ai.request.max_tokens`
- `gen_ai.response.id`, `gen_ai.response.finish_reasons`
- `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`

### Metrics

- `gen_ai.client.token.usage` (Counter): Token counts
- `gen_ai.client.operation.duration` (Histogram): Request latency
- `gen_ai.client.operation.error` (Counter): Error rates

## License

Apache License 2.0
