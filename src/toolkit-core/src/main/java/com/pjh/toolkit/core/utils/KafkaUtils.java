package com.pjh.toolkit.core.utils;

import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;

import com.pjh.toolkit.core.featureflags.IFeatureFlags;
import com.pjh.toolkit.core.logging.ILogger;
import com.pjh.toolkit.core.types.KafkaInputs;
import com.pjh.toolkit.core.types.SchemaFormat;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;

public final class KafkaUtils {
  private KafkaUtils() {}

  public static <K, V> KafkaInputs<K, V> prepareInputs(
    Properties schemaRegistryProps,
    Properties producerProps,
    Properties consumerProps,
    IFeatureFlags featureFlags,
    SchemaFormat schemaFormat,
    ILogger logger,
    String traceIdPath,
    String activitySourceName,
    String activityName
  ) {
    Objects.requireNonNull(schemaFormat, "schemaFormat must not be null");
    Objects.requireNonNull(schemaRegistryProps, "schemaRegistryProps must not be null");

    String srUrl = getRequired(schemaRegistryProps, AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG);
    int cacheCapacity = getInt(schemaRegistryProps, "schema.registry.cache.capacity", 1000);

    SchemaRegistryClient schemaRegistry = new CachedSchemaRegistryClient(srUrl, cacheCapacity);
    KafkaInputs<K, V> inputs = new KafkaInputs<>(schemaRegistry);

    if (producerProps != null) {
      Properties props = new Properties();
      props.putAll(producerProps);

      props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, srUrl);
      props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
      props.put(ProducerConfig.ACKS_CONFIG, "all");
      props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

      switch (schemaFormat) {
        case AVRO -> {
          props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
          props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        }
        case JSON -> {
          props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
          props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());
        }
        default -> throw new IllegalArgumentException("Unsupported schema format: " + schemaFormat);
      }

      inputs.producer = new KafkaProducer<>(props);
    }

    if (consumerProps != null) {
      Properties props = new Properties();
      props.putAll(consumerProps);

      props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, srUrl);
      props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

      switch (schemaFormat) {
        case AVRO -> {
          props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
          props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        }
        case JSON -> {
          props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());
          props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());
        }
        default -> throw new IllegalArgumentException("Unsupported schema format: " + schemaFormat);
      }

      inputs.consumer = new KafkaConsumer<>(props);
    }

    inputs.featureFlags = featureFlags;
    inputs.logger = logger;
    inputs.traceIdPath = traceIdPath;
    inputs.activitySourceName = activitySourceName;
    inputs.activityName = activityName;

    return inputs;
  }

  private static String getRequired(Properties props, String key) {
    String v = props.getProperty(key);
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException("Missing required config: " + key);
    }
    return v;
  }

  private static int getInt(Properties props, String key, int defaultValue) {
    String v = props.getProperty(key);
    if (v == null || v.isBlank()) return defaultValue;

    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid int value for config '" + key + "': '" + v + "'", e);
    }
  }
}
