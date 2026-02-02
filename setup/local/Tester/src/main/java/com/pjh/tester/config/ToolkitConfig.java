package com.pjh.tester.config;

import java.util.Map;
import java.util.Properties;

import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pjh.tester.models.MyKey;
import com.pjh.tester.models.MyValue;
import com.pjh.toolkit.core.featureflags.FeatureFlags;
import com.pjh.toolkit.core.featureflags.IFeatureFlags;
import com.pjh.toolkit.core.kafka.IKafka;
import com.pjh.toolkit.core.kafka.Kafka;
import com.pjh.toolkit.core.logging.ILogger;
import com.pjh.toolkit.core.logging.ToolkitLogger;
import com.pjh.toolkit.core.types.EnvNames;
import com.pjh.toolkit.core.types.FeatureFlagsInputs;
import com.pjh.toolkit.core.types.KafkaInputs;
import com.pjh.toolkit.core.types.LoggerInputs;
import com.pjh.toolkit.core.types.SchemaFormat;
import com.pjh.toolkit.core.utils.FeatureFlagsUtils;
import com.pjh.toolkit.core.utils.KafkaUtils;
import com.pjh.toolkit.core.utils.LoggerUtils;

@Configuration
public class ToolkitConfig {

  @Bean
  public ILogger toolkitLogger() {
    LoggerInputs inputs = LoggerUtils.prepareInputs(
      "my-app",
      "my-activity-source",
      "startup"
    );

    ILogger logger = new ToolkitLogger(inputs);

    // optional: "startup" log / scope
    try (AutoCloseable scope = logger.beginScope(
        Map.of("project.name", "myapp", "deployment.environment", "dev")
    )) {
      logger.log(com.pjh.toolkit.core.logging.MinLogLevel.INFORMATION, null, "Logger initialized");
    } catch (Exception ignored) {}

    return logger;
  }

  @Bean
  public IFeatureFlags toolkitFeatureFlag(ILogger logger) {
    FeatureFlagsInputs ffInputs = FeatureFlagsUtils.prepareInputs(
      System.getenv("LD_ENV_SDK_KEY"),
      System.getenv("LD_CONTEXT_API_KEY"),
      System.getenv("LD_CONTEXT_NAME"),
      EnvNames.dev,
      logger
    );

    return new FeatureFlags(ffInputs);
  }

  @Bean
  @Qualifier("jsonKafka")
  public IKafka<MyKey, MyValue> jsonKafka(
    ILogger logger,
    IFeatureFlags featureFlags
  ) {
    Properties schemaRegistryProps = new Properties();
    schemaRegistryProps.put("schema.registry.url", System.getenv("KAFKA_SCHEMA_REGISTRY_URL"));

    Properties producerProps = new Properties();
    producerProps.put("bootstrap.servers", System.getenv("KAFKA_CON_STR"));
    producerProps.put("client.id", "tester-json-producer");

    Properties consumerProps = new Properties();
    consumerProps.put("bootstrap.servers", System.getenv("KAFKA_CON_STR"));
    consumerProps.put("group.id", "tester-json-consumer-group");
    consumerProps.put("auto.offset.reset", "earliest");

    KafkaInputs<MyKey, MyValue> inputs = KafkaUtils.prepareInputs(
      schemaRegistryProps,
      producerProps,
      consumerProps,
      featureFlags,
      SchemaFormat.JSON,
      logger,
      "traceId",
      "tester-kafka-consumer",
      "tester-consume-event"
    );

    return new Kafka<>(inputs);
  }

  @Bean
  @Qualifier("avroKafka")
  public IKafka<GenericRecord, GenericRecord> avroKafka(
    ILogger logger,
    IFeatureFlags featureFlags
  ) {
    Properties schemaRegistryProps = new Properties();
    schemaRegistryProps.put("schema.registry.url", System.getenv("KAFKA_SCHEMA_REGISTRY_URL"));

    Properties producerProps = new Properties();
    producerProps.put("bootstrap.servers", System.getenv("KAFKA_CON_STR"));
    producerProps.put("client.id", "tester-avro-producer");

    Properties consumerProps = new Properties();
    consumerProps.put("bootstrap.servers", System.getenv("KAFKA_CON_STR"));
    consumerProps.put("group.id", "tester-avro-consumer-group");
    consumerProps.put("auto.offset.reset", "earliest");

    KafkaInputs<GenericRecord, GenericRecord> inputs = KafkaUtils.prepareInputs(
      schemaRegistryProps,
      producerProps,
      consumerProps,
      featureFlags,
      SchemaFormat.AVRO,
      logger,
      "traceId",
      "tester-kafka-consumer",
      "tester-consume-event"
    );

    return new Kafka<>(inputs);
  }
}
