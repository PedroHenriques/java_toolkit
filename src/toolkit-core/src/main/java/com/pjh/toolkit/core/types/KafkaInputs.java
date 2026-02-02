package com.pjh.toolkit.core.types;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

import com.pjh.toolkit.core.featureflags.IFeatureFlags;
import com.pjh.toolkit.core.logging.ILogger;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

public class KafkaInputs<K, V> {
  public SchemaRegistryClient schemaRegistry;
  public KafkaProducer<K, V> producer;
  public KafkaConsumer<K, V> consumer;
  public IFeatureFlags featureFlags;
  public ILogger logger;
  public String traceIdPath;
  public String activitySourceName;
  public String activityName;

  public KafkaInputs(SchemaRegistryClient schemaRegistry) {
    if (schemaRegistry == null) {
      throw new IllegalArgumentException("schemaRegistry is required");
    }

    this.schemaRegistry = schemaRegistry;
  }
}
