# OpenTelemetry Integration for LangChain4j

## Project Overview

**Title:** Add OpenTelemetry

**Description:** Add first-class OpenTelemetry (OTel) instrumentation to LangChain4j.

## Requirements

### Target & Compatibility

- Target: https://github.com/langchain4j/langchain4j (align with /docs/tutorials/observability.md)
- Java 17+
- New opt-in module: `langchain4j-opentelemetry`
- No API breaking changes

### Export & Configurationsdfsdasd

- **Export Format:** OTLP only (no direct Prometheus export)
- Support OTel Agent and SDK Autoconfigure
- Default OTLP export
- Respect OTEL\_\* environment variables

### Tracing

- Root span per AiService invocation
- Child spans for LLM/tool/guardrail operations
- CLIENT spans with GenAI semantic conventions
- Optional streaming events
- Context propagation

### Metrics

- Request count
- Latency histograms
- Error rate
- Retries
- Token usage

### Privacy & Performance

- Redaction ON by default
- Opt-in content capture with truncation
- Async export
- Head/tail sampling
- P95 overhead target: &lt;5%

### Deliverables

- Starters and documentation
- Samples (Spring Boot/Quarkus)
- MCP server wrapper to toggle telemetry and fetch recent traces/metrics