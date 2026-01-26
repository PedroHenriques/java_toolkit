package com.pjh.tester;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.pjh.toolkit.core.featureflags.FeatureFlags;
import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.logging.ToolkitLogger;
import com.pjh.toolkit.core.utilities.Utilities;

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
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/subscribe hit");

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
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/value hit");

    String ffKey = "ctt-net-toolkit-tester-consume-kafka-events";

    var curValue = this.featureFlags.getBoolFlagValue(ffKey, true);

    var msg = "The current value of the flag with key: " + ffKey + " is " + curValue;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/featureflag/cached-value")
  public String ffGetCachedValue() {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/cached-value hit");

    String ffKey = "ctt-net-toolkit-tester-consume-kafka-events";

    var cachedValue = FeatureFlags.getCachedBoolFlagValue(ffKey);

    var msg = "The cached value of the flag with key: " + ffKey + " is " + cachedValue;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/utilities/get-by-path")
  public String utilitiesGetByPath() {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /utilities/get-by-path hit");

    MyTestModel obj = new MyTestModel();

    var age = Utilities.getByPath(obj, "age");
    var innerAge = Utilities.getByPath(obj, "inner.innerAge");
    var listZero = Utilities.getByPath(obj, "list[0]");

    return "'age'=" + age + " | 'inner.innerAge'=" + innerAge + " | 'list[0]'=" + listZero;
  }

  @GetMapping("/utilities/add-to-path")
  public String utilitiesAddToPath() throws JsonProcessingException {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /utilities/add-to-path hit");

    ObjectMapper JSON = JsonMapper.builder()
      .findAndAddModules()
      .build();

    MyTestModel obj = new MyTestModel();

    String startJson = JSON.writeValueAsString(obj);

    Utilities.addToPath(obj, "age", 123);
    Utilities.addToPath(obj, "inner.innerAge", 987);
    Utilities.addToPath(obj, "list[0]", "first list item");

    String endJson = JSON.writeValueAsString(obj);
    
    return "Start state: " + startJson + "<br>End state: " + endJson;
  }
}
