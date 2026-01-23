package com.pjh.tester;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pjh.toolkit.core.logging.MinLogLevel;
import com.pjh.toolkit.core.logging.ToolkitLogger;

@RestController
public class RootController {

  private final ToolkitLogger logger;

  public RootController(ToolkitLogger logger) {
    this.logger = logger;
  }

  @GetMapping("/")
  public String hello() {
    logger.log(MinLogLevel.INFORMATION, null, "GET / hit");
    return "hello world";
  }
}
