package com.secretsanta.group

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "com.secretsanta.group",
        "com.secretsanta.infrastructure"
    ]
)
class GroupServiceApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<GroupServiceApplication>(*args)
        }
    }
}
