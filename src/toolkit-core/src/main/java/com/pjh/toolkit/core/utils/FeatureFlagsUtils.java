package com.pjh.toolkit.core.utils;

import java.time.Duration;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.pjh.toolkit.core.logging.ILogger;
import com.pjh.toolkit.core.types.EnvNames;
import com.pjh.toolkit.core.types.FeatureFlagsInputs;

public final class FeatureFlagsUtils {
  private FeatureFlagsUtils() {}

  public static FeatureFlagsInputs prepareInputs(
      String envSdkKey,
      String contextKey,
      String contextName,
      EnvNames envName,
      ILogger logger
  ) {
    LDConfig config = new LDConfig.Builder()
        .startWait(Duration.ofSeconds(5))
        .build();

    LDClient client = new LDClient(envSdkKey, config);

    LDContext context = LDContext.builder(contextKey)
        .kind("application")
        .name(contextName)
        .set("env", envName.toString())
        .build();

    return new FeatureFlagsInputs(client, context, logger);
  }
}
