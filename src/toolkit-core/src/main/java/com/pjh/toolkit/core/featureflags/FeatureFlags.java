package com.pjh.toolkit.core.featureflags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;
import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.logging.ToolkitLogger;
import com.pjh.toolkit.core.types.FeatureFlagsInputs;

public class FeatureFlags implements IFeatureFlags {

  private static final Map<String, Boolean> FLAG_VALUES = new ConcurrentHashMap<>();
  private final FeatureFlagsInputs inputs;

  public FeatureFlags(FeatureFlagsInputs inputs) {
    this.inputs = inputs;
  }

  public static boolean getCachedBoolFlagValue(String flagKey) {
    Boolean v = FLAG_VALUES.get(flagKey);
    if (v == null) {
      throw new IllegalStateException("Flag value not cached for key: " + flagKey);
    }
    return v;
  }

  @Override
  public boolean getBoolFlagValue(String flagKey, boolean defaultValue) {
    LDClient client = inputs.getClient();
    boolean value = client.boolVariation(flagKey, inputs.getContext(), defaultValue);
    FLAG_VALUES.put(flagKey, value);
    return value;
  }

  @Override
  public void subscribeToValueChanges(String flagKey, Consumer<FlagValueChangeEvent> handler) {
    // Register change handler
    FlagValueChangeListener listener = new FlagValueChangeListener() {
      @Override
      public void onFlagValueChange(FlagValueChangeEvent ev) {
        if (!flagKey.equals(ev.getKey())) return;

        // Cache the new value (boolean flags only)
        FLAG_VALUES.put(ev.getKey(), ev.getNewValue().booleanValue());

        if (handler != null) handler.accept(ev);

        ToolkitLogger logger = inputs.getLogger();
        if (logger != null) {
          logger.log(
            MinLogLevel.INFORMATION,
            null,
            "The feature flag with key '{}' changed value from '{}' to '{}'.",
            flagKey,
            ev.getOldValue(),
            ev.getNewValue()
          );
        }
      }
    };

    // NOTE: exact method name depends on the LD Java Server SDK version
    // Common form:
    inputs.getClient()
      .getFlagTracker()
      .addFlagValueChangeListener(flagKey, inputs.getContext(), listener);

    // Prime cache with current value
    getBoolFlagValue(flagKey, false);
  }
}
