package com.charter.camunda.worker.validation.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreaker camundaCircuitBreaker(CircuitBreakerRegistry registry, MeterRegistry meterRegistry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("camunda");
        
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit Breaker state changed: {} -> {}", 
                    event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState()))
            .onError(event -> 
                log.error("Circuit Breaker recorded error: {}", event.getThrowable().getMessage()))
            .onCallNotPermitted(event -> 
                log.warn("Circuit Breaker call not permitted - Circuit is OPEN"));
        
        return circuitBreaker;
    }
}
