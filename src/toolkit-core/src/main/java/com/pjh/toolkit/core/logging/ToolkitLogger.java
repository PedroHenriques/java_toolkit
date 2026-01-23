package com.pjh.toolkit.core.logging;

import com.pjh.toolkit.core.types.LoggerInputs;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolkitLogger implements ILogger {

  private static final ContextKey<Map<String, Object>> SCOPE_KEY =
    ContextKey.named("toolkit.logger.scope");

  private final LoggerInputs inputs;

  public ToolkitLogger(LoggerInputs inputs) {
    this.inputs = inputs;
  }

  @Override
  public AutoCloseable beginScope(Map<String, Object> scope) {
    Map<String, Object> merged = new LinkedHashMap<>();

    Map<String, Object> existing = Context.current().get(SCOPE_KEY);
    if (existing != null) merged.putAll(existing);

    if (scope != null) merged.putAll(scope);

    Context next = Context.current().with(SCOPE_KEY, Map.copyOf(merged));
    Scope otelScope = next.makeCurrent();
    return otelScope::close;
  }

  @Override
  public void log(MinLogLevel level, Throwable ex, String message, Object... args) {
    if (level.rank < inputs.minLevel().rank) {
      return;
    }

    String body = format(message, args);

    LogRecordBuilder b = inputs.logger()
      .logRecordBuilder()
      .setSeverity(level.otelSeverity)
      .setSeverityText(level.name())
      .setTimestamp(Instant.now());

    if (ex != null) {
      b.setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("exception.type"), ex.getClass().getName());
      b.setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("exception.message"), String.valueOf(ex.getMessage()));
    }

    Map<String, Object> scope = Context.current().get(SCOPE_KEY);
    if (scope != null && !scope.isEmpty()) {
      AttributesBuilder ab = io.opentelemetry.api.common.Attributes.builder();
      for (Map.Entry<String, Object> e : scope.entrySet()) {
        if (e.getKey() == null) continue;
        Object v = e.getValue();
        ab.put(e.getKey(), v == null ? "null" : String.valueOf(v));
      }
      b.setAllAttributes(ab.build());
    }

    b.setBody(body).emit();
  }

  private static String format(String template, Object... args) {
    if (template == null) return "";
    if (args == null || args.length == 0) return template;

    String out = template;
    for (Object arg : args) {
      int idx = out.indexOf("{}");
      if (idx < 0) break;
      String rep = (arg == null) ? "null" : String.valueOf(arg);
      out = out.substring(0, idx) + rep + out.substring(idx + 2);
    }
    return out;
  }
}
