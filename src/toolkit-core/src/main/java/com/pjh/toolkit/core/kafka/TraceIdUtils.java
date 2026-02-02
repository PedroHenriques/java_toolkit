package com.pjh.toolkit.core.kafka;

import java.util.Locale;
import java.util.UUID;

import io.opentelemetry.api.trace.Span;

final class TraceIdUtils {
  private TraceIdUtils() {}

  static String currentTraceIdOrRandom() {
    try {
      var sc = Span.current().getSpanContext();
      if (sc != null && sc.isValid()) return sc.getTraceId();
    } catch (Exception ignored) {}
    // 32 hex chars
    return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
  }

  static boolean isValidTraceId(String traceId) {
    if (traceId == null) return false;
    if (traceId.length() != 32) return false;
    for (int i = 0; i < 32; i++) {
      char c = traceId.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) return false;
    }
    // OpenTelemetry considers all-zeros invalid
    return !traceId.equals("00000000000000000000000000000000");
  }
}
