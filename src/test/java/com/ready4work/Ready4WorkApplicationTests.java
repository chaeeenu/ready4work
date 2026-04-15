package com.ready4work;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "env.ODSAY_API_KEY=test-key")
class Ready4WorkApplicationTests {

	@Test
	void contextLoads() {
	}

}
