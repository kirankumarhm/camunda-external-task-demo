# Circuit Breaker Implementation

## Overview

All workers implement **Resilience4j Circuit Breaker** to prevent cascading failures and provide graceful degradation when Camunda Engine or external services are unavailable.

## Circuit Breaker States

```
CLOSED → OPEN → HALF_OPEN → CLOSED
```

### States Explained:

1. **CLOSED** (Normal Operation)
   - All requests pass through
   - Failures are counted
   - If failure rate > 50%, transition to OPEN

2. **OPEN** (Failure Mode)
   - All requests are rejected immediately
   - No calls to Camunda
   - After 10 seconds, transition to HALF_OPEN

3. **HALF_OPEN** (Testing Recovery)
   - Allow 3 test requests
   - If successful, transition to CLOSED
   - If failed, transition back to OPEN

---

## Configuration

### application.properties

```properties
# Circuit Breaker Configuration
resilience4j.circuitbreaker.instances.camunda.registerHealthIndicator=true
resilience4j.circuitbreaker.instances.camunda.slidingWindowSize=10
resilience4j.circuitbreaker.instances.camunda.minimumNumberOfCalls=5
resilience4j.circuitbreaker.instances.camunda.permittedNumberOfCallsInHalfOpenState=3
resilience4j.circuitbreaker.instances.camunda.automaticTransitionFromOpenToHalfOpenEnabled=true
resilience4j.circuitbreaker.instances.camunda.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.camunda.failureRateThreshold=50
```

### Parameters:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `slidingWindowSize` | 10 | Number of calls to track |
| `minimumNumberOfCalls` | 5 | Min calls before calculating failure rate |
| `failureRateThreshold` | 50% | Threshold to open circuit |
| `waitDurationInOpenState` | 10s | Wait time before HALF_OPEN |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Test calls in HALF_OPEN |

---

## Metrics

### Circuit Breaker Metrics

```bash
# Circuit breaker state
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.state

# Failure rate
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.failure.rate

# Calls
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls
```

### Health Indicator

```bash
curl http://localhost:8081/actuator/health
```

**Response when circuit is OPEN:**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "camunda": {
          "status": "CIRCUIT_OPEN",
          "failureRate": "75.0%",
          "slowCallRate": "0.0%",
          "bufferedCalls": 10,
          "failedCalls": 7,
          "slowCalls": 0,
          "notPermittedCalls": 5
        }
      }
    }
  }
}
```

---

## Testing Circuit Breaker

### 1. Start Workers
```bash
mvn clean install
cd order-validation-worker && mvn spring-boot:run
```

### 2. Stop Camunda Engine
```bash
# Stop Camunda to simulate failure
# Circuit should open after 5 failed calls
```

### 3. Trigger Requests
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8080/engine-rest/process-definition/key/orderProcess/start \
    -H "Content-Type: application/json" \
    -d '{
      "variables": {
        "orderId": {"value": "ORD-'$i'", "type": "String"},
        "amount": {"value": 5000.00, "type": "Double"},
        "customerId": {"value": "CUST-001", "type": "String"}
      }
    }'
  sleep 1
done
```

### 4. Check Circuit State
```bash
curl http://localhost:8081/actuator/health
```

### 5. Expected Logs
```
WARN Circuit Breaker state changed: CLOSED -> OPEN
WARN Circuit Breaker call not permitted - Circuit is OPEN
```

### 6. Restart Camunda
```bash
# After 10 seconds, circuit transitions to HALF_OPEN
# 3 test calls are permitted
# If successful, circuit transitions to CLOSED
```

---

## Benefits

✅ **Fail Fast** - Immediate rejection when service is down
✅ **Prevent Cascading Failures** - Stop calling failing services
✅ **Automatic Recovery** - Self-healing after timeout
✅ **Resource Protection** - Prevent thread exhaustion
✅ **Metrics Integration** - Monitor circuit state

---

## Production Recommendations

### Tuning for Production

```properties
# More conservative settings
resilience4j.circuitbreaker.instances.camunda.slidingWindowSize=20
resilience4j.circuitbreaker.instances.camunda.minimumNumberOfCalls=10
resilience4j.circuitbreaker.instances.camunda.failureRateThreshold=60
resilience4j.circuitbreaker.instances.camunda.waitDurationInOpenState=30s
```

### Monitoring Alerts

Set up alerts for:
- Circuit state transitions (CLOSED → OPEN)
- High failure rates (> 50%)
- Circuit open duration (> 1 minute)

### Fallback Strategies

When circuit is OPEN:
1. Return cached data
2. Queue requests for later processing
3. Return default/degraded response
4. Notify operations team

---

## Summary

✅ Step 1: Error Handling & Logging - **DONE**
✅ Step 2: Distributed Tracing - **DONE**
✅ Step 3: Health & Performance Monitoring - **DONE**
✅ Step 4: Circuit Breakers - **DONE**
⏭️ Step 5: Kubernetes Orchestration
