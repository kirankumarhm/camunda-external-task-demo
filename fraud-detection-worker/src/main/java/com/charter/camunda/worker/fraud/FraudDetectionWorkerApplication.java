package com.charter.camunda.worker.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FraudDetectionWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionWorkerApplication.class, args);
    }
}
