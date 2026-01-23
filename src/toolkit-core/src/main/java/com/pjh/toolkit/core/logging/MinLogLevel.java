package com.pjh.toolkit.core.logging;

import io.opentelemetry.api.logs.Severity;

import java.util.Locale;

public enum MinLogLevel {
  TRACE(0, Severity.TRACE),
  DEBUG(1, Severity.DEBUG),
  INFORMATION(2, Severity.INFO),
  WARNING(3, Severity.WARN),
  ERROR(4, Severity.ERROR),
  CRITICAL(5, Severity.FATAL);

  public final int rank;
  public final Severity otelSeverity;

  MinLogLevel(int rank, Severity otelSeverity) {
    this.rank = rank;
    this.otelSeverity = otelSeverity;
  }

  public static MinLogLevel fromEnv(String value) {
    String v = (value == null ? "warning" : value).trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "trace" -> TRACE;
      case "debug" -> DEBUG;
      case "information", "info" -> INFORMATION;
      case "warning", "warn" -> WARNING;
      case "error" -> ERROR;
      case "critical", "fatal" -> CRITICAL;
      default -> throw new IllegalArgumentException(
          "The desired minimum log level '" + value + "' is not valid."
      );
    };
  }
}
