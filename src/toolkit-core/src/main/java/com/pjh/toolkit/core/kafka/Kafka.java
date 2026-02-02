package com.pjh.toolkit.core.kafka;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.pjh.toolkit.core.featureflags.IFeatureFlags;
import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.types.KafkaInputs;
import com.pjh.toolkit.core.utilities.Utilities;

public class Kafka<K, V> implements IKafka<K, V> {

  private final KafkaInputs<K, V> inputs;

  public Kafka(KafkaInputs<K, V> inputs) {
    this.inputs = Objects.requireNonNull(inputs);
  }

  @Override
  public void publish(
      String topicName,
      K key,
      V value,
      BiConsumer<org.apache.kafka.clients.producer.RecordMetadata, Exception> handler
  ) {
    if (inputs.producer == null) {
      throw new IllegalStateException("A KafkaProducer instance was not provided in the inputs.");
    }

    // Inject traceId into message value (if configured)
    if (inputs.traceIdPath != null && !inputs.traceIdPath.isBlank() && value != null) {
      String traceId = TraceIdUtils.currentTraceIdOrRandom();
      try {
        Utilities.addToPath(value, inputs.traceIdPath, traceId);
      } catch (Exception ex) {
        if (inputs.logger != null) {
          inputs.logger.log(
            MinLogLevel.WARNING, ex,
            "Failed to set traceId at path '{}' on Kafka message value.", inputs.traceIdPath
          );
        }
      }
    }

    ProducerRecord<K, V> record = new ProducerRecord<>(topicName, key, value);

    inputs.producer.send(record, (metadata, exception) -> {
      if (handler != null) handler.accept(metadata, exception);
    });

    inputs.producer.flush();
  }

  @Override
  public void subscribe(
      Collection<String> topics,
      KafkaMessageHandler<K, V> handler,
      double pollingDelaySec
  ) {
    if (inputs.consumer == null) {
      throw new IllegalStateException("A KafkaConsumer instance was not provided in the inputs.");
    }

    Thread t = new Thread(() -> {
      inputs.consumer.subscribe(topics);

      try {
        while (!Thread.currentThread().isInterrupted()) {
          try {
            var records = inputs.consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<K, V> rec : records) {
              // Ensure traceId is set (read from message or generate)
              if (inputs.traceIdPath != null && !inputs.traceIdPath.isBlank() && rec.value() != null) {
                String msgTraceId = null;
                try {
                  Object node = Utilities.getByPath(rec.value(), inputs.traceIdPath);
                  if (node instanceof String s) msgTraceId = s;
                } catch (Exception ignored) {}

                String chosen = TraceIdUtils.isValidTraceId(msgTraceId)
                  ? msgTraceId
                  : TraceIdUtils.currentTraceIdOrRandom();

                if (inputs.logger != null && (msgTraceId == null || !chosen.equalsIgnoreCase(msgTraceId))) {
                  inputs.logger.log(
                      MinLogLevel.WARNING,
                      null,
                      "Kafka message from topic '{}' partition '{}' offset '{}' had invalid traceId at '{}': '{}'. Using '{}'.",
                      rec.topic(), rec.partition(), rec.offset(), inputs.traceIdPath, msgTraceId, chosen
                  );
                }
              }

              handler.handle(rec, null);
            }

          } catch (Exception ex) {
            handler.handle(null, ex);
          }

          if (pollingDelaySec > 0) {
            try {
              Thread.sleep((long) (pollingDelaySec * 1000));
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
          }
        }
      } catch (Exception outer) {
        try {
          handler.handle(null, outer);
        } catch (Exception ignored) {}
      }
    }, "Toolkit-Kafka-Consumer");

    t.setDaemon(true);
    t.start();
  }

  @Override
  public void subscribe(
      Collection<String> topics,
      KafkaMessageHandler<K, V> handler,
      String featureFlagKey,
      double pollingDelaySec
  ) {
    if (inputs.featureFlags == null) {
      throw new IllegalStateException("An instance of IFeatureFlags was not provided in the inputs.");
    }

    IFeatureFlags ff = inputs.featureFlags;
    AtomicReference<Thread> runningThread = new AtomicReference<>(null);
    AtomicBoolean running = new AtomicBoolean(false);

    Runnable start = () -> {
      if (running.compareAndSet(false, true)) {
        // Start a new consumer thread
        Thread t = new Thread(() -> {
          try {
            subscribe(topics, handler, pollingDelaySec);
          } finally {
            running.set(false);
          }
        }, "Toolkit-Kafka-FF-Consumer");

        t.setDaemon(true);
        runningThread.set(t);
        t.start();
      }
    };

    Runnable stop = () -> {
      Thread t = runningThread.getAndSet(null);
      if (t != null) {
        t.interrupt();
      }
      running.set(false);
    };

    // Start if enabled
    if (ff.getBoolFlagValue(featureFlagKey, false)) {
      start.run();
    }

    // React to changes
    ff.subscribeToValueChanges(featureFlagKey, ev -> {
      boolean enabled = ev.getNewValue().booleanValue();
      if (enabled) start.run();
      else stop.run();
    });
  }

  @Override
  public void commit(ConsumerRecord<K, V> record) {
    if (inputs.consumer == null) {
      throw new IllegalStateException("A KafkaConsumer instance was not provided in the inputs.");
    }
    // Commit current offsets
    inputs.consumer.commitSync();
  }
}
