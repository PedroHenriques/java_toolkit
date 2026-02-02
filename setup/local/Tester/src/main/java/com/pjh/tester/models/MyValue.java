package com.pjh.tester.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyValue(
  @JsonProperty("prop1") String prop1,
  @JsonProperty("prop2") String prop2,
  @JsonProperty("prop3") boolean prop3,
  @JsonProperty("prop4") double prop4,
  @JsonProperty("prop5") int prop5
) {
  public MyValue {
    if (prop1 == null || prop1.isBlank()) {
      throw new IllegalArgumentException("prop1 required");
    }

    if (prop2 == null || prop2.isBlank()) {
      throw new IllegalArgumentException("prop2 required");
    }
  }
}