package com.charter.camunda.worker.payment.handler;

import com.charter.camunda.worker.payment.exception.PaymentException;
import com.charter.camunda.worker.payment.exception.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ExternalTaskErrorHandler {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_TIMEOUT = 5000;

    public void handleError(ExternalTask task, ExternalTaskService service, Exception exception) {
        String taskId = task.getId();
        String correlationId = getCorrelationId(task);

        log.error("Error processing task | taskId={} | correlationId={} | error={}", 
                taskId, correlationId, exception.getMessage(), exception);

        if (exception instanceof PaymentException) {
            handlePaymentError(task, service, (PaymentException) exception, correlationId);
        } else if (exception instanceof TechnicalException) {
            handleTechnicalError(task, service, (TechnicalException) exception, correlationId);
        } else {
            handleUnknownError(task, service, exception, correlationId);
        }
    }

    private void handlePaymentError(ExternalTask task, ExternalTaskService service, 
                                    PaymentException ex, String correlationId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("errorCode", ex.getErrorCode());
        variables.put("errorMessage", ex.getMessage());
        variables.put("correlationId", correlationId);

        if (ex.isRetryable()) {
            int retries = task.getRetries() != null ? task.getRetries() : MAX_RETRIES;
            long retryTimeout = calculateRetryTimeout(MAX_RETRIES - retries);

            log.warn("Payment error (retryable) | taskId={} | correlationId={} | retriesLeft={} | retryIn={}ms", 
                    task.getId(), correlationId, retries, retryTimeout);

            service.handleFailure(task, ex.getMessage(), ex.toString(), retries - 1, retryTimeout);
        } else {
            log.error("Payment error (non-retryable) | taskId={} | correlationId={} | errorCode={}", 
                    task.getId(), correlationId, ex.getErrorCode());

            service.handleBpmnError(task, ex.getErrorCode(), ex.getMessage(), variables);
        }
    }

    private void handleTechnicalError(ExternalTask task, ExternalTaskService service, 
                                     TechnicalException ex, String correlationId) {
        int retries = task.getRetries() != null ? task.getRetries() : MAX_RETRIES;
        long retryTimeout = calculateRetryTimeout(MAX_RETRIES - retries);

        log.error("Technical error | taskId={} | correlationId={} | errorCode={} | retriesLeft={} | retryIn={}ms", 
                task.getId(), correlationId, ex.getErrorCode(), retries, retryTimeout);

        service.handleFailure(task, ex.getMessage(), ex.toString(), retries - 1, retryTimeout);
    }

    private void handleUnknownError(ExternalTask task, ExternalTaskService service, 
                                   Exception ex, String correlationId) {
        int retries = task.getRetries() != null ? task.getRetries() : MAX_RETRIES;
        long retryTimeout = calculateRetryTimeout(MAX_RETRIES - retries);

        log.error("Unknown error | taskId={} | correlationId={} | retriesLeft={} | retryIn={}ms", 
                task.getId(), correlationId, retries, retryTimeout);

        service.handleFailure(task, ex.getMessage(), ex.toString(), retries - 1, retryTimeout);
    }

    private long calculateRetryTimeout(int attemptNumber) {
        return BASE_RETRY_TIMEOUT * (long) Math.pow(2, attemptNumber);
    }

    private String getCorrelationId(ExternalTask task) {
        try {
            return task.getVariable("correlationId");
        } catch (Exception e) {
            return task.getProcessInstanceId();
        }
    }
}
