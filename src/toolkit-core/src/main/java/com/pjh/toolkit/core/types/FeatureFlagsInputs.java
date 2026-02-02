package com.pjh.toolkit.core.types;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.pjh.toolkit.core.logging.ILogger;

public class FeatureFlagsInputs {
  private final LDClient client;
  private final LDContext context;
  private final ILogger logger;

  public FeatureFlagsInputs(LDClient client, LDContext context, ILogger logger) {
    this.client = client;
    this.context = context;
    this.logger = logger;
  }

  public LDClient getClient() { return client; }
  public LDContext getContext() { return context; }
  public ILogger getLogger() { return logger; }
}
