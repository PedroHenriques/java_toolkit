package com.pjh.toolkit.core.logging;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;

import java.util.Locale;
import java.util.Map;

public interface ILogger {

  AutoCloseable beginScope(Map<String, Object> scope);

  void log(MinLogLevel level, Throwable ex, String message, Object... args);

  static Span setTraceIds(
    String traceId,
    String activitySourceName,
    String activityName,
    String spanId
  ) {
    String normalizedTraceId = normalizeTraceId(traceId);
    String normalizedSpanId = normalizeSpanId(spanId);

    SpanContext parentSpanContext = SpanContext.createFromRemoteParent(
      normalizedTraceId,
      normalizedSpanId,
      TraceFlags.getSampled(),
      TraceState.getDefault()
    );

    Tracer tracer = GlobalOpenTelemetry.getTracer(activitySourceName);
    Context parent = Context.current().with(Span.wrap(parentSpanContext));

    return tracer.spanBuilder(activityName)
      .setParent(parent)
      .setSpanKind(SpanKind.INTERNAL)
      .startSpan();
  }

  static Span setTraceIds(
    String traceId,
    String activitySourceName,
    String activityName
  ) {
    return setTraceIds(traceId, activitySourceName, activityName, null);
  }

  private static String normalizeTraceId(String traceId) {
    if (traceId != null) {
      String t = traceId.trim().toLowerCase(Locale.ROOT);
      if (t.matches("^[0-9a-f]{32}$")) return t;
    }
    return TraceId.fromLongs(
      java.util.concurrent.ThreadLocalRandom.current().nextLong(),
      java.util.concurrent.ThreadLocalRandom.current().nextLong()
    );
  }

  private static String normalizeSpanId(String spanId) {
    if (spanId != null && !spanId.isBlank()) {
      String s = spanId.trim().toLowerCase(Locale.ROOT);
      if (s.matches("^[0-9a-f]{16}$")) return s;
    }
    return SpanId.fromLong(java.util.concurrent.ThreadLocalRandom.current().nextLong());
  }
}
