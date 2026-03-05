# Distributed Tracing with OpenTelemetry

## Overview

All workers are instrumented with **Micrometer Tracing** and **OpenTelemetry** to provide end-to-end distributed tracing across the entire Camunda process execution.

## Architecture

```
Process Start → Order Validation → Fraud Detection → Payment → Process End
     |                |                  |              |
     └────────────────┴──────────────────┴──────────────┘
                    Single Trace ID
```

## Features

✅ **Automatic trace propagation** across all workers
✅ **Trace ID in logs** for correlation
✅ **Span visualization** in Zipkin UI
✅ **Performance metrics** with latency tracking
✅ **Error tracking** with stack traces

---

## Setup

### 1. Start Zipkin

```bash
cd camunda-external-task-demo
docker-compose -f docker-compose-zipkin.yml up -d
```

**Zipkin UI**: http://localhost:9411

### 2. Start All Services

```bash
# Terminal 1 - Camunda Engine
cd camunda-engine
mvn spring-boot:run

# Terminal 2 - Order Validation Worker
cd order-validation-worker
mvn spring-boot:run

# Terminal 3 - Fraud Detection Worker
cd fraud-detection-worker
mvn spring-boot:run

# Terminal 4 - Payment Worker
cd payment-worker
mvn spring-boot:run
```

### 3. Trigger a Process

```bash
curl -X POST http://localhost:8080/engine-rest/process-definition/key/orderProcess/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "orderId": {"value": "ORD-12345", "type": "String"},
      "amount": {"value": 5000.00, "type": "Double"},
      "customerId": {"value": "CUST-001", "type": "String"}
    }
  }'
```

### 4. View Traces in Zipkin

1. Open http://localhost:9411
2. Click **"Run Query"**
3. See the complete trace with all 3 workers

---

## Trace Structure

### Example Trace

```
Trace ID: 1a2b3c4d5e6f7g8h
├── Span: order-validation-worker (2.1s)
│   ├── Task: validateOrder
│   └── Duration: 2100ms
├── Span: fraud-detection-worker (1.5s)
│   ├── Task: checkForFraud
│   └── Duration: 1500ms
└── Span: payment-worker (2.0s)
    ├── Task: processPayment
    └── Duration: 2000ms

Total Duration: 5.6s
```

---

## Log Format with Trace IDs

**Before (without tracing):**
```
INFO  Task started | taskId=abc123 | correlationId=xyz789
```

**After (with tracing):**
```
INFO  [order-validation-worker,1a2b3c4d5e6f7g8h,9i0j1k2l3m4n5o6p] Task started | taskId=abc123
```

Format: `[service-name,traceId,spanId]`

---

## Configuration

### application.properties

```properties
# Tracing Configuration
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

**Parameters:**
- `sampling.probability=1.0` - Trace 100% of requests (use 0.1 for 10% in production)
- `zipkin.tracing.endpoint` - Zipkin server URL
- `logging.pattern.level` - Include trace/span IDs in logs

---

## Zipkin UI Features

### 1. Search Traces
- Filter by service name
- Filter by duration
- Filter by tags

### 2. View Trace Details
- See all spans in timeline
- View span duration
- See tags and annotations
- View error stack traces

### 3. Dependencies
- Visualize service dependencies
- See call patterns
- Identify bottlenecks

---

## Production Considerations

### Sampling Rate
```properties
# Development: Trace everything
management.tracing.sampling.probability=1.0

# Production: Trace 10%
management.tracing.sampling.probability=0.1
```

### Storage Backend
```yaml
# Use Elasticsearch for production
services:
  zipkin:
    image: openzipkin/zipkin:latest
    environment:
      - STORAGE_TYPE=elasticsearch
      - ES_HOSTS=elasticsearch:9200
```

### Performance Impact
- **Minimal overhead**: ~1-2ms per span
- **Async export**: Non-blocking
- **Sampling**: Reduces load in production

---

## Troubleshooting

### Traces Not Appearing in Zipkin

1. **Check Zipkin is running:**
   ```bash
   curl http://localhost:9411/health
   ```

2. **Check worker logs for trace IDs:**
   ```bash
   # Should see: [service-name,traceId,spanId]
   tail -f logs/order-validation-worker.log
   ```

3. **Verify Zipkin endpoint:**
   ```bash
   curl -X POST http://localhost:9411/api/v2/spans \
     -H "Content-Type: application/json" \
     -d '[]'
   ```

### Missing Spans

- Ensure all workers have tracing dependencies
- Check `sampling.probability` is > 0
- Verify network connectivity to Zipkin

---

## Benefits Demonstrated

✅ **End-to-End Visibility** - See complete request flow
✅ **Performance Analysis** - Identify slow services
✅ **Error Tracking** - Trace errors to source
✅ **Dependency Mapping** - Understand service relationships
✅ **Debugging** - Correlate logs with trace IDs

---

## Next Steps

- Add custom spans for business logic
- Integrate with Jaeger (alternative to Zipkin)
- Add trace context to Camunda process variables
- Export traces to APM tools (Datadog, New Relic)
