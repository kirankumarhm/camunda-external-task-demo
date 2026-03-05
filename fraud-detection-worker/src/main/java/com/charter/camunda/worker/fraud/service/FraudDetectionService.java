package com.charter.camunda.worker.fraud.service;

import com.charter.camunda.worker.fraud.exception.FraudException;
import com.charter.camunda.worker.fraud.handler.ExternalTaskErrorHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.ExternalTaskClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class FraudDetectionService {

    @Value("${camunda.bpm.client.base-url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    @Value("${camunda.bpm.client.worker-id:fraud-detection-worker}")
    private String workerId;

    @Autowired
    private ExternalTaskErrorHandler errorHandler;

    @Autowired
    private Tracer tracer;

    private ExternalTaskClient client;
    private final Random random = new Random();

    @PostConstruct
    public void startWorker() {
        log.info("🚀 Starting Fraud Detection Worker...");
        log.info("Configuration | baseUrl={} | topic=fraud-detection | workerId={}", camundaUrl, workerId);

        client = ExternalTaskClient.create()
                .baseUrl(camundaUrl)
                .workerId(workerId)
                .asyncResponseTimeout(10000)
                .build();

        client.subscribe("fraud-detection")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {
                    String correlationId = getCorrelationId(externalTask);
                    String taskId = externalTask.getId();
                    long startTime = System.currentTimeMillis();

                    Span span = tracer.nextSpan().name("fraud-detection-task").start();
                    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                        log.info("🔍 Task started | taskId={} | correlationId={} | processInstanceId={}", 
                                taskId, correlationId, externalTask.getProcessInstanceId());

                        String orderId = externalTask.getVariable("orderId");
                        Double amount = externalTask.getVariable("amount");
                        String customerId = externalTask.getVariable("customerId");

                        log.info("Fraud check | orderId={} | amount={} | customerId={} | correlationId={}", 
                                orderId, amount, customerId, correlationId);

                        FraudCheckResult result = checkForFraud(orderId, amount, customerId);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("fraudCheckPassed", result.isPassed());
                        variables.put("fraudScore", result.getScore());
                        variables.put("fraudReason", result.getReason());
                        variables.put("checkedBy", workerId);
                        variables.put("correlationId", correlationId);

                        if (result.isPassed()) {
                            long duration = System.currentTimeMillis() - startTime;
                            span.tag("orderId", orderId);
                            span.tag("taskId", taskId);
                            span.tag("worker", workerId);
                            span.tag("result", "success");
                            span.tag("fraudScore", String.format("%.2f", result.getScore()));
                            log.info("✅ Task completed | taskId={} | correlationId={} | orderId={} | fraudScore={} | duration={}ms", 
                                    taskId, correlationId, orderId, String.format("%.2f", result.getScore()), duration);
                            externalTaskService.complete(externalTask, variables);
                        } else {
                            span.tag("orderId", orderId);
                            span.tag("taskId", taskId);
                            span.tag("worker", workerId);
                            span.tag("result", "fraud_detected");
                            span.tag("fraudScore", String.format("%.2f", result.getScore()));
                            span.tag("error", result.getReason());
                            log.warn("❌ Fraud detected | taskId={} | correlationId={} | orderId={} | fraudScore={} | reason={}", 
                                    taskId, correlationId, orderId, String.format("%.2f", result.getScore()), result.getReason());
                            externalTaskService.handleBpmnError(externalTask,
                                    "FRAUD_DETECTED", result.getReason(), variables);
                        }

                    } catch (Exception e) {
                        span.tag("error", e.getMessage());
                        span.error(e);
                        errorHandler.handleError(externalTask, externalTaskService, e);
                    } finally {
                        span.end();
                    }
                })
                .open();

        log.info("✅ Fraud Detection Worker subscribed to 'fraud-detection' topic");
    }

    @PreDestroy
    public void stopWorker() {
        if (client != null) {
            log.info("🛑 Stopping Fraud Detection Worker...");
            client.stop();
            log.info("✅ Fraud Detection Worker stopped");
        }
    }

    private FraudCheckResult checkForFraud(String orderId, Double amount, String customerId) {
        log.debug("🔍 Running fraud detection | orderId={} | amount={} | customerId={}", orderId, amount, customerId);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FraudException("Fraud check interrupted", "FRAUD_CHECK_INTERRUPTED", true);
        }

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new FraudException("Order ID is required for fraud check", "MISSING_ORDER_ID", false);
        }

        double fraudScore = random.nextDouble() * 100;

        if (amount > 50000) {
            fraudScore += 30;
        }

        if (fraudScore > 80) {
            return new FraudCheckResult(false, fraudScore, 
                    "High fraud score detected: " + String.format("%.2f", fraudScore));
        }

        if (orderId.contains("SUSPICIOUS")) {
            return new FraudCheckResult(false, 95.0, 
                    "Suspicious order pattern detected");
        }

        return new FraudCheckResult(true, fraudScore, "No fraud detected");
    }

    private String getCorrelationId(org.camunda.bpm.client.task.ExternalTask task) {
        try {
            return task.getVariable("correlationId");
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    @Data
    @AllArgsConstructor
    private static class FraudCheckResult {
        private boolean passed;
        private double score;
        private String reason;
    }
}
