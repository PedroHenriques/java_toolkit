package com.pjh.tester.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyKey(
  @JsonProperty("id") String id
) {
  public MyKey {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id required");
    }
  }
}