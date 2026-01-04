package com.pjh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("Integration")
class ApiApplicationIntegrationTests {
	@Test
	void ScaffoldingIntegrationTest() {
		assertEquals(1, 1);
	}
}
