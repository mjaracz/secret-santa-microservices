package com.secretsanta.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"server.port=0",
		"spring.kafka.bootstrap-servers=localhost:9092",
		"spring.kafka.consumer.group-id=api-gateway-test",
		"kafka.topics.user-commands=user.commands",
		"kafka.topics.group-commands=group.commands",
		"kafka.topics.user-events=user.events",
		"kafka.topics.group-events=group.events"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
