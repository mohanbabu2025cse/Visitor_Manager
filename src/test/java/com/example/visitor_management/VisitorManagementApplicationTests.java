package com.example.visitor_management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:context-test", "app.sms.enabled=false"})
class VisitorManagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
