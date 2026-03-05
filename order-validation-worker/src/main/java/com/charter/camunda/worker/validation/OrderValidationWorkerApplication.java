package com.charter.camunda.worker.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderValidationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderValidationWorkerApplication.class, args);
    }
}
