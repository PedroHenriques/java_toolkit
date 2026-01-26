package com.pjh.tester;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.pjh.toolkit.core.featureflags.FeatureFlags;
import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.logging.ToolkitLogger;

@RestController
public class RootController {

  private final ToolkitLogger logger;
  private final FeatureFlags featureFlags;

  public RootController(ToolkitLogger logger, FeatureFlags ff) {
    this.logger = logger;
    this.featureFlags = ff;
  }

  @GetMapping("/")
  public String hello() {
    logger.log(MinLogLevel.INFORMATION, null, "GET / hit");
    return "hello world";
  }

  @GetMapping("/featureflag/subscribe")
  public String ffSubscribe() {
    logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/subscribe hit");

    String ffKey = "ctt-net-toolkit-tester-consume-kafka-events";

    this.featureFlags.subscribeToValueChanges(
      ffKey,
      (FlagValueChangeEvent ev) -> {
        this.logger.log(
          MinLogLevel.INFORMATION, null, "Received Feature Flag event: old value '" + ev.getOldValue().stringValue() + "' to new value '" + ev.getNewValue().stringValue() + "'"
        );
      }
    );

    var msg = "Subscribed to flag with key: " + ffKey;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/featureflag/value")
  public String ffGetValue() {
    logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/value hit");

    String ffKey = "ctt-net-toolkit-tester-consume-kafka-events";

    var curValue = this.featureFlags.getBoolFlagValue(ffKey, true);

    var msg = "The current value of the flag with key: " + ffKey + " is " + curValue;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/featureflag/cached-value")
  public String ffGetCachedValue() {
    logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/cached-value hit");

    String ffKey = "ctt-net-toolkit-tester-consume-kafka-events";

    var cachedValue = FeatureFlags.getCachedBoolFlagValue(ffKey);

    var msg = "The cached value of the flag with key: " + ffKey + " is " + cachedValue;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }
}
