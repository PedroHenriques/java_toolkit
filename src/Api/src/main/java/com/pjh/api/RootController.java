package com.pjh.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {
  @GetMapping("/")
  public String hello() {
    return "hello world";
  }
}
