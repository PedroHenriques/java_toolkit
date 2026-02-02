package com.pjh.tester;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.pjh.tester.models.MyKey;
import com.pjh.tester.models.MyTestModel;
import com.pjh.tester.models.MyValue;
import com.pjh.toolkit.core.featureflags.FeatureFlags;
import com.pjh.toolkit.core.featureflags.IFeatureFlags;
import com.pjh.toolkit.core.kafka.IKafka;
import com.pjh.toolkit.core.logging.ILogger;
import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.utilities.Utilities;

@RestController
public class RootController {
  private final String KAFKA_JSON_TOPIC = "testJsonTopic";
  private final String KAFKA_AVRO_TOPIC = "testAvroTopic";
  private final String FF_KEY = "ctt-net-toolkit-tester-consume-kafka-events";
  private final ILogger logger;
  private final IFeatureFlags featureFlags;
  private final IKafka<MyKey, MyValue> jsonKafka;
  private final IKafka<GenericRecord, GenericRecord> avroKafka;

  public RootController(
    ILogger logger, IFeatureFlags ff, @Qualifier("jsonKafka") IKafka<MyKey, MyValue> jsonKafka,
    @Qualifier("avroKafka") IKafka<GenericRecord, GenericRecord> avroKafka
  ) {
    this.logger = logger;
    this.featureFlags = ff;
    this.jsonKafka = jsonKafka;
    this.avroKafka = avroKafka;

    this.kafkaSubscribeJson();
    this.kafkaSubscribeAvro();
  }

  @GetMapping("/")
  public String hello() {
    logger.log(MinLogLevel.INFORMATION, null, "GET / hit");
    return "hello world";
  }

  @GetMapping("/featureflag/subscribe")
  public String ffSubscribe() {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/subscribe hit");

    this.featureFlags.subscribeToValueChanges(
      this.FF_KEY,
      (FlagValueChangeEvent ev) -> {
        this.logger.log(
          MinLogLevel.INFORMATION, null, "Received Feature Flag event: old value '" + ev.getOldValue().stringValue() + "' to new value '" + ev.getNewValue().stringValue() + "'"
        );
      }
    );

    var msg = "Subscribed to flag with key: " + this.FF_KEY;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/featureflag/value")
  public String ffGetValue() {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/value hit");

    var curValue = this.featureFlags.getBoolFlagValue(this.FF_KEY, true);

    var msg = "The current value of the flag with key: " + this.FF_KEY + " is " + curValue;
    this.logger.log(MinLogLevel.INFORMATION, null, msg);

    return msg;
  }

  @GetMapping("/featureflag/cached-value")
  public String ffGetCachedValue() {
    this.logger.log(MinLogLevel.INFORMATION, null, "GET /featureflag/cached-value hit");

    var cachedValue = FeatureFlags.getCachedBoolFlagValue(this.FF_KEY);

    var msg = "The cached value of the flag with key: " + this.FF_KEY + " is " + cachedValue;
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

  @PostMapping("/kafka/json")
  public CompletableFuture<String> kafkaPublishJson() {
    this.logger.log(MinLogLevel.INFORMATION, null, "POST /kafka/json hit");

    MyKey key = new MyKey(UUID.randomUUID().toString());
    MyValue value = new MyValue("prop1", "prop2", true, 2.3, 765);

    CompletableFuture<String> responseFuture = new CompletableFuture<>();

    this.jsonKafka.publish(
      this.KAFKA_JSON_TOPIC,
      key,
      value,
      (recMetadata, ex) -> {
        if (ex != null) {
          this.logger.log(MinLogLevel.ERROR, ex, "Kafka publish failed");

          responseFuture.completeExceptionally(ex);
          return;
        }

        String msg = "Published event in Kafka topic '" + recMetadata.topic() + "' on partition '" + recMetadata.partition() + "' and offset '" + recMetadata.offset() + "'";
        
        this.logger.log(MinLogLevel.INFORMATION, null, msg);
        responseFuture.complete(msg);
      }
    );

    return responseFuture;
  }

  public final void kafkaSubscribeJson() {
    ObjectMapper JSON = JsonMapper.builder()
      .findAndAddModules()
      .build();

    this.jsonKafka.subscribe(
      List.of(this.KAFKA_JSON_TOPIC),
      (record, ex) -> {
        if (ex != null) {
          this.logger.log(
            MinLogLevel.ERROR, ex, "There was an error consuming from the '" + this.KAFKA_JSON_TOPIC + "' Kafka topic."
          );
          return;
        }

        this.logger.log(
          MinLogLevel.INFORMATION, null,
          "Received event from Kafka topic '" + this.KAFKA_JSON_TOPIC + "' with key: " + JSON.writeValueAsString(record.key()) + " and value: " + JSON.writeValueAsString(record.value())
        );
      },
      this.FF_KEY,
      0
    );
  }

  @PostMapping("/kafka/avro")
  public CompletableFuture<String> kafkaPublishAvro() {
    this.logger.log(MinLogLevel.INFORMATION, null, "POST /kafka/avro hit");

    Schema keySchema = new Schema.Parser().parse("""
      {
        "type": "record",
        "name": "MyKey",
        "namespace": "Tester.Services",
        "fields": [
          { "name": "id", "type": "string" }
        ]
      }
    """);

    Schema valueSchema = new Schema.Parser().parse("""
      {
        "type": "record",
        "name": "MyValue",
        "namespace": "Tester.Services",
        "fields": [
          { "name": "name", "type": "string" }
        ]
      }
    """);

    GenericRecord key = new GenericData.Record(keySchema);
    key.put("id", Instant.now().toString());

    GenericRecord value = new GenericData.Record(valueSchema);
    value.put("name", "hello world: " + LocalDateTime.now());

    CompletableFuture<String> responseFuture = new CompletableFuture<>();

    this.avroKafka.publish(
      this.KAFKA_AVRO_TOPIC,
      key,
      value,
      (recMetadata, ex) -> {
        if (ex != null) {
          this.logger.log(MinLogLevel.ERROR, ex, "Kafka publish failed");

          responseFuture.completeExceptionally(ex);
          return;
        }

        String msg = "Published event in Kafka topic '" + recMetadata.topic() + "' on partition '" + recMetadata.partition() + "' and offset '" + recMetadata.offset() + "'";
        
        this.logger.log(MinLogLevel.INFORMATION, null, msg);
        responseFuture.complete(msg);
      }
    );

    return responseFuture;
  }

  public final void kafkaSubscribeAvro() {
    this.avroKafka.subscribe(
      List.of(this.KAFKA_AVRO_TOPIC),
      (record, ex) -> {
        if (ex != null) {
          this.logger.log(
            MinLogLevel.ERROR, ex, "There was an error consuming from the '" + this.KAFKA_AVRO_TOPIC + "' Kafka topic."
          );
          return;
        }

        this.logger.log(
          MinLogLevel.INFORMATION, null,
          "Received event from Kafka topic '" + this.KAFKA_AVRO_TOPIC + "' with key: " + record.key() + " and value: " + record.value()
        );
      },
      this.FF_KEY,
      0
    );
  }
}
