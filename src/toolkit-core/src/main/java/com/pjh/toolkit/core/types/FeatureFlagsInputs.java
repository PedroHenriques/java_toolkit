package com.pjh.toolkit.core.types;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.pjh.toolkit.core.logging.ToolkitLogger;

public class FeatureFlagsInputs {
  private final LDClient client;
  private final LDContext context;
  private final ToolkitLogger logger; // nullable allowed

  public FeatureFlagsInputs(LDClient client, LDContext context, ToolkitLogger logger) {
    this.client = client;
    this.context = context;
    this.logger = logger;
  }

  public LDClient getClient() { return client; }
  public LDContext getContext() { return context; }
  public ToolkitLogger getLogger() { return logger; }
}
