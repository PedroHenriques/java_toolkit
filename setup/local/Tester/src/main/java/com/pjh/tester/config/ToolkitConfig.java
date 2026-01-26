package com.pjh.tester.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pjh.toolkit.core.featureflags.FeatureFlags;
import com.pjh.toolkit.core.logging.ToolkitLogger;
import com.pjh.toolkit.core.types.EnvNames;
import com.pjh.toolkit.core.types.FeatureFlagsInputs;
import com.pjh.toolkit.core.types.LoggerInputs;
import com.pjh.toolkit.core.utils.FeatureFlagsUtils;
import com.pjh.toolkit.core.utils.LoggerUtils;

@Configuration
public class ToolkitConfig {

  @Bean
  public ToolkitLogger toolkitLogger() {
    LoggerInputs inputs = LoggerUtils.prepareInputs(
      "my-app",
      "my-activity-source",
      "startup"
    );

    ToolkitLogger logger = new ToolkitLogger(inputs);

    // optional: "startup" log / scope
    try (AutoCloseable scope = logger.beginScope(
        Map.of("project.name", "myapp", "deployment.environment", "dev")
    )) {
      logger.log(com.pjh.toolkit.core.logging.MinLogLevel.INFORMATION, null, "Logger initialized");
    } catch (Exception ignored) {}

    return logger;
  }

  @Bean
  public FeatureFlags toolkitFeatureFlag(ToolkitLogger logger) {
    FeatureFlagsInputs ffInputs = FeatureFlagsUtils.prepareInputs(
      System.getenv("LD_ENV_SDK_KEY"),
      System.getenv("LD_CONTEXT_API_KEY"),
      System.getenv("LD_CONTEXT_NAME"),
      EnvNames.dev,
      logger
    );

    return new FeatureFlags(ffInputs);
  }
}
