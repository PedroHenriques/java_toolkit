package com.pjh.toolkit.core.kafka;

import java.util.Collection;
import java.util.function.BiConsumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public interface IKafka<K, V> {
  void publish(
    String topicName,
    K key,
    V value,
    BiConsumer<RecordMetadata, Exception> handler
  );

  void subscribe(
    Collection<String> topics,
    KafkaMessageHandler<K, V> handler,
    double pollingDelaySec
  );

  void subscribe(
    Collection<String> topics,
    KafkaMessageHandler<K, V> handler,
    String featureFlagKey,
    double pollingDelaySec
  );

  void commit(ConsumerRecord<K, V> record);

  @FunctionalInterface
  interface KafkaMessageHandler<K, V> {
    void handle(ConsumerRecord<K, V> record, Exception error) throws Exception;
  }
}
