package com.pjh.toolkit.core.types;

import com.pjh.toolkit.core.logging.MinLogLevel;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;

public record LoggerInputs(
    OpenTelemetrySdk sdk,
    OpenTelemetry openTelemetry,
    Logger logger,
    Tracer tracer,
    MinLogLevel minLevel
) {}
