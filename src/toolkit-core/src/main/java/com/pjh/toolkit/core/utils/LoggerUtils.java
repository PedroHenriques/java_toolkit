package com.pjh.toolkit.core.utils;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.types.LoggerInputs;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.ServiceAttributes;

public final class LoggerUtils {

  private LoggerUtils() {}

  public enum ExporterMode {
    BATCH,
    SYNC;

    public static ExporterMode fromEnv(String value) {
      String v = (value == null ? "batch" : value).trim().toLowerCase(Locale.ROOT);
      return switch (v) {
        case "batch" -> BATCH;
        case "sync", "simple" -> SYNC;
        default -> {
          System.out.println("⚠️ WARNING: exporter mode '" + value + "' is not supported, defaulting to 'batch'!");
          yield BATCH;
        }
      };
    }
  }

  public static LoggerInputs prepareInputs(
    String logCategory,
    String activitySourceName,
    String activityName
  ) {
    String logDestUri = envRequired("LOG_DESTINATION_URI");

    String serviceName = envOrWarn("SERVICE_NAME", "N/A");
    String serviceVersion = envOrWarn("SERVICE_VERSION", "N/A");
    String projectName = envOrWarn("PROJECT_NAME", "N/A");
    String deploymentEnv = envOrWarn("DEPLOYMENT_ENV", "N/A");

    ExporterMode exporterMode = ExporterMode.fromEnv(System.getenv("EXPORTER_MODE"));
    MinLogLevel minLevel = MinLogLevel.fromEnv(System.getenv("LOG_LEVEL"));

    Resource resource = createResource(serviceName, serviceVersion, projectName, deploymentEnv);

    LogRecordExporter otlpLogs = OtlpGrpcLogRecordExporter.builder()
      .setEndpoint(asOtlpEndpoint(logDestUri))
      .setTimeout(Duration.ofSeconds(10))
      .build();

    LogRecordExporter consoleLogs = SystemOutLogRecordExporter.create();

    LogRecordExporter combinedLogs = LogRecordExporter.composite(consoleLogs, otlpLogs);

    LogRecordProcessor logsProcessor = (exporterMode == ExporterMode.SYNC)
      ? SimpleLogRecordProcessor.create(combinedLogs)
      : BatchLogRecordProcessor.builder(combinedLogs)
        .setScheduleDelay(Duration.ofMillis(800))
        .build();


    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
      .setResource(resource)
      .addLogRecordProcessor(logsProcessor)
      .build();

    SpanExporter otlpTraces = OtlpGrpcSpanExporter.builder()
      .setEndpoint(asOtlpEndpoint(logDestUri))
      .setTimeout(Duration.ofSeconds(10))
      .build();

    SpanExporter consoleTraces = new LoggingSpanExporter();

    SpanExporter combinedTraces = SpanExporter.composite(consoleTraces, otlpTraces);

    SpanProcessor spanProcessor = (exporterMode == ExporterMode.SYNC)
      ? SimpleSpanProcessor.create(combinedTraces)
      : BatchSpanProcessor.builder(combinedTraces)
        .setScheduleDelay(Duration.ofMillis(800))
        .build();


    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
      .setResource(resource)
      .addSpanProcessor(spanProcessor)
      .build();

    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
      .setLoggerProvider(loggerProvider)
      .setTracerProvider(tracerProvider)
      .build();

    GlobalOpenTelemetry.set(sdk);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> dispose(sdk), "toolkit-otel-shutdown"));

    LoggerProvider lp = sdk.getLogsBridge();
    Logger otelLogger = lp.loggerBuilder(logCategory).build();
    Tracer tracer = sdk.getTracer(activitySourceName);

    String randomTraceId = io.opentelemetry.api.trace.TraceId.fromLongs(
      java.util.concurrent.ThreadLocalRandom.current().nextLong(),
      java.util.concurrent.ThreadLocalRandom.current().nextLong()
    );
    String randomSpanId = io.opentelemetry.api.trace.SpanId.fromLong(
      java.util.concurrent.ThreadLocalRandom.current().nextLong()
    );

    io.opentelemetry.api.trace.Span span =
      com.pjh.toolkit.core.logging.ILogger.setTraceIds(
        randomTraceId, activitySourceName, activityName, randomSpanId
      );

    span.end();

    return new LoggerInputs(sdk, sdk, otelLogger, tracer, minLevel);
  }

  private static void dispose(OpenTelemetrySdk sdk) {
    try {
      System.out.println(".Java Toolkit: LoggerUtils.dispose() called.");
    } catch (Exception ignored) {}

    try {
      sdk.getSdkLoggerProvider().close();
    } catch (Exception ignored) {}

    try {
      sdk.getSdkTracerProvider().close();
    } catch (Exception ignored) {}
  }

  private static Resource createResource(
      String serviceName, String serviceVersion, String projectName, String deploymentEnv
  ) {
    Attributes attrs = Attributes.of(
      ServiceAttributes.SERVICE_NAME, serviceName,
      ServiceAttributes.SERVICE_VERSION, serviceVersion,
      AttributeKey.stringKey("project.name"), projectName,
      AttributeKey.stringKey("deployment.environment"), deploymentEnv
    );

    return Resource.getDefault().merge(Resource.create(attrs));
  }

  private static String envRequired(String name) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("❌ ERROR: " + name + " is not set!");
    }
    return v.trim();
  }

  private static String envOrWarn(String name, String fallback) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) {
      System.out.println("⚠️ WARNING: " + name + " is not set!");
      return fallback;
    }
    return v.trim();
  }

  private static String asOtlpEndpoint(String uri) {
    URI u = URI.create(uri);
    if (u.getScheme() == null) {
      return "http://" + uri;
    }
    return uri;
  }
}
