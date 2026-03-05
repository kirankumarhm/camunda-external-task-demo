# Worker Health and Performance Monitoring

## Overview

All workers are instrumented with **Spring Boot Actuator** and **Micrometer** for comprehensive health checks and performance monitoring.

## Features

✅ **Custom Health Indicators** - Camunda connectivity check
✅ **Performance Metrics** - Task count, duration, success/failure rates
✅ **JVM Metrics** - Memory, threads, GC
✅ **Liveness/Readiness Probes** - Kubernetes-ready
✅ **Prometheus Export** - Ready for Grafana dashboards

---

## Health Endpoints

### 1. Overall Health
```bash
curl http://localhost:8081/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "camundaHealthIndicator": {
      "status": "UP",
      "details": {
        "camundaUrl": "http://localhost:8080/engine-rest",
        "status": "Connected"
      }
    },
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### 2. Liveness Probe (Kubernetes)
```bash
curl http://localhost:8081/actuator/health/liveness
```

### 3. Readiness Probe (Kubernetes)
```bash
curl http://localhost:8081/actuator/health/readiness
```

---

## Metrics Endpoints

### 1. All Metrics
```bash
curl http://localhost:8081/actuator/metrics
```

### 2. Worker-Specific Metrics

**Tasks Processed:**
```bash
curl http://localhost:8081/actuator/metrics/worker.tasks.processed
```

**Tasks Succeeded:**
```bash
curl http://localhost:8081/actuator/metrics/worker.tasks.succeeded
```

**Tasks Failed:**
```bash
curl http://localhost:8081/actuator/metrics/worker.tasks.failed
```

**Task Duration:**
```bash
curl http://localhost:8081/actuator/metrics/worker.task.duration
```

### 3. JVM Metrics

**Memory Usage:**
```bash
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

**Thread Count:**
```bash
curl http://localhost:8081/actuator/metrics/jvm.threads.live
```

**GC Metrics:**
```bash
curl http://localhost:8081/actuator/metrics/jvm.gc.pause
```

---

## Prometheus Endpoint

All metrics are exported in Prometheus format:

```bash
curl http://localhost:8081/actuator/prometheus
```

**Sample Output:**
```
# HELP worker_tasks_processed_total Total number of tasks processed
# TYPE worker_tasks_processed_total counter
worker_tasks_processed_total{application="order-validation-worker",worker="order-validation",} 150.0

# HELP worker_tasks_succeeded_total Number of successfully processed tasks
# TYPE worker_tasks_succeeded_total counter
worker_tasks_succeeded_total{application="order-validation-worker",worker="order-validation",} 145.0

# HELP worker_tasks_failed_total Number of failed tasks
# TYPE worker_tasks_failed_total counter
worker_tasks_failed_total{application="order-validation-worker",worker="order-validation",} 5.0

# HELP worker_task_duration_seconds Task processing duration
# TYPE worker_task_duration_seconds summary
worker_task_duration_seconds_count{application="order-validation-worker",worker="order-validation",} 150.0
worker_task_duration_seconds_sum{application="order-validation-worker",worker="order-validation",} 225.5
```

---

## Kubernetes Health Probes

### Deployment YAML

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-validation-worker
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: worker
        image: order-validation-worker:latest
        ports:
        - containerPort: 8081
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
```

---

## Monitoring Dashboard

### Key Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `worker.tasks.processed` | Total tasks processed | - |
| `worker.tasks.succeeded` | Successful tasks | - |
| `worker.tasks.failed` | Failed tasks | > 5% failure rate |
| `worker.task.duration` | Task processing time | > 5 seconds |
| `jvm.memory.used` | JVM memory usage | > 80% |
| `jvm.threads.live` | Active threads | > 200 |
| `camundaHealthIndicator` | Camunda connectivity | DOWN |

### Success Rate Calculation

```
Success Rate = (tasks_succeeded / tasks_processed) * 100
```

### Average Task Duration

```
Avg Duration = task_duration_sum / task_duration_count
```

---

## Testing Metrics

### 1. Start All Workers
```bash
mvn clean install
cd order-validation-worker && mvn spring-boot:run &
cd fraud-detection-worker && mvn spring-boot:run &
cd payment-worker && mvn spring-boot:run &
```

### 2. Generate Load
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

### 3. Check Metrics
```bash
# Order Validation Worker
curl http://localhost:8081/actuator/metrics/worker.tasks.processed

# Fraud Detection Worker
curl http://localhost:8082/actuator/metrics/worker.tasks.processed

# Payment Worker
curl http://localhost:8083/actuator/metrics/worker.tasks.processed
```

---

## Grafana Dashboard (Coming Next)

Metrics are ready for Grafana visualization:
- Task throughput over time
- Success/failure rates
- Task duration percentiles (p50, p95, p99)
- JVM memory and GC metrics
- Worker health status

---

## Troubleshooting

### Health Check Fails

**Symptom:** `/actuator/health` returns DOWN

**Check:**
1. Camunda Engine is running
2. Network connectivity to Camunda
3. Worker logs for errors

### Metrics Not Updating

**Symptom:** Metrics show 0 or don't change

**Check:**
1. Tasks are being processed
2. Metrics are being recorded in code
3. MeterRegistry is properly injected

### High Memory Usage

**Symptom:** `jvm.memory.used` > 80%

**Actions:**
1. Check for memory leaks
2. Increase JVM heap size: `-Xmx2g`
3. Review GC metrics

---

## Next Steps

✅ Step 1: Error Handling & Logging - **DONE**
✅ Step 2: Distributed Tracing - **DONE**
✅ Step 3: Health & Performance Monitoring - **DONE**
⏭️ Step 4: Circuit Breakers
⏭️ Step 5: Kubernetes Orchestration
