package com.triagedeck.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.triagedeck")
@EnableScheduling
public class TriageDeckApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriageDeckApplication.class, args);
    }
}
