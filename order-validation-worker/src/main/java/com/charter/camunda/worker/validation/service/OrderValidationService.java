package com.charter.camunda.worker.validation.service;

import com.charter.camunda.worker.validation.exception.ValidationException;
import com.charter.camunda.worker.validation.handler.ExternalTaskErrorHandler;
import com.charter.camunda.worker.validation.metrics.WorkerMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.ExternalTaskClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderValidationService {

    @Value("${camunda.bpm.client.base-url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    @Value("${camunda.bpm.client.worker-id:order-validation-worker}")
    private String workerId;

    @Autowired
    private ExternalTaskErrorHandler errorHandler;

    @Autowired
    private WorkerMetrics metrics;

    @Autowired
    private CircuitBreaker camundaCircuitBreaker;

    @Autowired
    private Tracer tracer;

    private ExternalTaskClient client;

    @PostConstruct
    public void startWorker() {
        log.info("🚀 Starting Order Validation Worker...");
        log.info("Configuration | baseUrl={} | topic=order-validation | workerId={}", camundaUrl, workerId);

        client = ExternalTaskClient.create()
                .baseUrl(camundaUrl)
                .workerId(workerId)
                .asyncResponseTimeout(10000)
                .build();

        client.subscribe("order-validation")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {
                    Span span = tracer.nextSpan().name("order-validation-task").start();
                    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                        String correlationId = UUID.randomUUID().toString();
                        String taskId = externalTask.getId();
                        long startTime = System.currentTimeMillis();

                        span.tag("orderId", externalTask.getVariable("orderId"));
                        span.tag("taskId", taskId);
                        span.tag("worker", "order-validation");

                        metrics.recordTaskProcessed();

                        try {
                            log.info("📦 Task started | taskId={} | correlationId={} | processInstanceId={}", 
                                    taskId, correlationId, externalTask.getProcessInstanceId());

                            String orderId = externalTask.getVariable("orderId");
                            Double amount = externalTask.getVariable("amount");
                            String customerId = externalTask.getVariable("customerId");

                            log.info("Order details | orderId={} | amount={} | customerId={} | correlationId={}", 
                                    orderId, amount, customerId, correlationId);

                            boolean isValid = validateOrder(orderId, amount, customerId);

                            Map<String, Object> variables = new HashMap<>();
                            variables.put("orderValid", isValid);
                            variables.put("validationTimestamp", System.currentTimeMillis());
                            variables.put("validatedBy", workerId);
                            variables.put("correlationId", correlationId);

                            if (isValid) {
                                long duration = System.currentTimeMillis() - startTime;
                                metrics.recordTaskSucceeded();
                                metrics.recordTaskDuration(duration);
                                span.tag("result", "success");
                                log.info("✅ Task completed | taskId={} | correlationId={} | orderId={} | duration={}ms", 
                                        taskId, correlationId, orderId, duration);
                                externalTaskService.complete(externalTask, variables);
                            } else {
                                metrics.recordTaskFailed();
                                span.tag("result", "validation-failed");
                                log.warn("❌ Validation failed | taskId={} | correlationId={} | orderId={}", 
                                        taskId, correlationId, orderId);
                                variables.put("validationError", "Order validation failed");
                                externalTaskService.handleBpmnError(externalTask, 
                                        "ORDER_INVALID", "Order validation failed", variables);
                            }

                        } catch (Exception e) {
                            metrics.recordTaskFailed();
                            span.tag("error", "true");
                            span.tag("error.message", e.getMessage());
                            errorHandler.handleError(externalTask, externalTaskService, e);
                        }
                    } finally {
                        span.end();
                    }
                })
                .open();

        log.info("✅ Order Validation Worker subscribed to 'order-validation' topic");
    }

    @PreDestroy
    public void stopWorker() {
        if (client != null) {
            log.info("🛑 Stopping Order Validation Worker...");
            client.stop();
            log.info("✅ Order Validation Worker stopped");
        }
    }

    private boolean validateOrder(String orderId, Double amount, String customerId) {
        log.debug("🔍 Validating order | orderId={} | amount={} | customerId={}", orderId, amount, customerId);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Validation interrupted", "VALIDATION_INTERRUPTED", true);
        }

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new ValidationException("Order ID is required", "MISSING_ORDER_ID", false);
        }

        if (amount == null || amount <= 0) {
            throw new ValidationException("Amount must be positive", "INVALID_AMOUNT", false);
        }

        if (amount > 100000) {
            throw new ValidationException("Amount exceeds maximum limit of 100000", "AMOUNT_EXCEEDS_LIMIT", false);
        }

        if (customerId == null || customerId.trim().isEmpty()) {
            throw new ValidationException("Customer ID is required", "MISSING_CUSTOMER_ID", false);
        }

        return true;
    }
}
