package com.secretsanta.group;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
	"com.secretsanta.group",
	"com.secretsanta.infrastructure"
})
public class GroupServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GroupServiceApplication.class, args);
	}

}
