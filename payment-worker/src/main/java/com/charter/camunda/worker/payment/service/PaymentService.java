package com.charter.camunda.worker.payment.service;

import com.charter.camunda.worker.payment.exception.PaymentException;
import com.charter.camunda.worker.payment.handler.ExternalTaskErrorHandler;
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
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    @Value("${camunda.bpm.client.base-url:http://localhost:8080/engine-rest}")
    private String camundaUrl;

    @Value("${camunda.bpm.client.worker-id:payment-worker}")
    private String workerId;

    @Autowired
    private ExternalTaskErrorHandler errorHandler;

    @Autowired
    private Tracer tracer;

    private ExternalTaskClient client;

    @PostConstruct
    public void startWorker() {
        log.info("🚀 Starting Payment Processing Worker...");
        log.info("Configuration | baseUrl={} | topic=payment-processing | workerId={}", camundaUrl, workerId);

        client = ExternalTaskClient.create()
                .baseUrl(camundaUrl)
                .workerId(workerId)
                .asyncResponseTimeout(10000)
                .build();

        client.subscribe("payment-processing")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {
                    String correlationId = getCorrelationId(externalTask);
                    String taskId = externalTask.getId();
                    long startTime = System.currentTimeMillis();

                    Span span = tracer.nextSpan().name("payment-processing-task").start();
                    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                        log.info("💳 Task started | taskId={} | correlationId={} | processInstanceId={}", 
                                taskId, correlationId, externalTask.getProcessInstanceId());

                        String orderId = externalTask.getVariable("orderId");
                        Double amount = externalTask.getVariable("amount");
                        String customerId = externalTask.getVariable("customerId");

                        log.info("Payment processing | orderId={} | amount={} | customerId={} | correlationId={}", 
                                orderId, amount, customerId, correlationId);

                        PaymentResult result = processPayment(orderId, amount, customerId);

                        Map<String, Object> variables = new HashMap<>();
                        variables.put("paymentSuccess", result.isSuccess());
                        variables.put("paymentId", result.getPaymentId());
                        variables.put("transactionId", result.getTransactionId());
                        variables.put("paymentTimestamp", System.currentTimeMillis());
                        variables.put("processedBy", workerId);
                        variables.put("correlationId", correlationId);

                        if (result.isSuccess()) {
                            long duration = System.currentTimeMillis() - startTime;
                            span.tag("orderId", orderId);
                            span.tag("taskId", taskId);
                            span.tag("worker", workerId);
                            span.tag("result", "success");
                            span.tag("transactionId", result.getTransactionId());
                            log.info("✅ Task completed | taskId={} | correlationId={} | orderId={} | transactionId={} | duration={}ms", 
                                    taskId, correlationId, orderId, result.getTransactionId(), duration);
                            externalTaskService.complete(externalTask, variables);
                        } else {
                            span.tag("orderId", orderId);
                            span.tag("taskId", taskId);
                            span.tag("worker", workerId);
                            span.tag("result", "payment_failed");
                            span.tag("error", result.getErrorMessage());
                            log.warn("❌ Payment failed | taskId={} | correlationId={} | orderId={} | reason={}", 
                                    taskId, correlationId, orderId, result.getErrorMessage());
                            variables.put("paymentError", result.getErrorMessage());
                            externalTaskService.handleBpmnError(externalTask,
                                    "PAYMENT_FAILED", result.getErrorMessage(), variables);
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

        log.info("✅ Payment Worker subscribed to 'payment-processing' topic");
    }

    @PreDestroy
    public void stopWorker() {
        if (client != null) {
            log.info("🛑 Stopping Payment Worker...");
            client.stop();
            log.info("✅ Payment Worker stopped");
        }
    }

    private PaymentResult processPayment(String orderId, Double amount, String customerId) {
        log.debug("💳 Processing payment | orderId={} | amount={} | customerId={}", orderId, amount, customerId);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment processing interrupted", "PAYMENT_INTERRUPTED", true);
        }

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new PaymentException("Order ID is required for payment", "MISSING_ORDER_ID", false);
        }

        if (amount == null || amount <= 0) {
            throw new PaymentException("Invalid payment amount", "INVALID_AMOUNT", false);
        }

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12);

        if (amount > 75000) {
            return new PaymentResult(false, null, null, 
                    "Payment declined - amount exceeds limit");
        }

        if (Math.random() < 0.05) {
            return new PaymentResult(false, null, null, 
                    "Payment gateway timeout");
        }

        return new PaymentResult(true, paymentId, transactionId, null);
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
    private static class PaymentResult {
        private boolean success;
        private String paymentId;
        private String transactionId;
        private String errorMessage;
    }
}
