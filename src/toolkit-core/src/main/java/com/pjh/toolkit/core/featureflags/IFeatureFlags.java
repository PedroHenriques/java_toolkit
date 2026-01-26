package com.pjh.toolkit.core.featureflags;

import java.util.function.Consumer;

import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;

public interface IFeatureFlags {
  boolean getBoolFlagValue(String flagKey, boolean defaultValue);

  void subscribeToValueChanges(String flagKey, Consumer<FlagValueChangeEvent> handler);

  static boolean getCachedBoolFlagValue(String flagKey) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
