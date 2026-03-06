package com.charter.camunda.worker.payment.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class WorkerController {

    @Value("${camunda.bpm.client.worker-id:payment-worker}")
    private String workerId;

    @Value("${camunda.bpm.client.base-url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("workerId", workerId);
        info.put("workerType", "payment-processing");
        info.put("topic", "payment-processing");
        info.put("camundaUrl", camundaUrl);
        info.put("status", "running");
        
        log.debug("Worker info requested");
        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> getHealth() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("worker", workerId);
        return ResponseEntity.ok(health);
    }
}
