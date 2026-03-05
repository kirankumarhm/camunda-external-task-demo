package com.charter.camunda.worker.validation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WorkerMetrics {

    private final Counter tasksProcessed;
    private final Counter tasksSucceeded;
    private final Counter tasksFailed;
    private final Timer taskDuration;

    public WorkerMetrics(MeterRegistry registry) {
        this.tasksProcessed = Counter.builder("worker.tasks.processed")
                .description("Total number of tasks processed")
                .tag("worker", "order-validation")
                .register(registry);

        this.tasksSucceeded = Counter.builder("worker.tasks.succeeded")
                .description("Number of successfully processed tasks")
                .tag("worker", "order-validation")
                .register(registry);

        this.tasksFailed = Counter.builder("worker.tasks.failed")
                .description("Number of failed tasks")
                .tag("worker", "order-validation")
                .register(registry);

        this.taskDuration = Timer.builder("worker.task.duration")
                .description("Task processing duration")
                .tag("worker", "order-validation")
                .register(registry);
    }

    public void recordTaskProcessed() {
        tasksProcessed.increment();
    }

    public void recordTaskSucceeded() {
        tasksSucceeded.increment();
    }

    public void recordTaskFailed() {
        tasksFailed.increment();
    }

    public void recordTaskDuration(long durationMs) {
        taskDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
