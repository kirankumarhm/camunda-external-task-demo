package com.charter.camunda.worker.validation.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class CamundaHealthIndicator implements HealthIndicator {

    @Value("${camunda.bpm.client.base-url}")
    private String camundaUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Health health() {
        try {
            String versionUrl = camundaUrl + "/version";
            restTemplate.getForObject(versionUrl, String.class);
            
            return Health.up()
                    .withDetail("camundaUrl", camundaUrl)
                    .withDetail("status", "Connected")
                    .build();
        } catch (Exception e) {
            log.error("Camunda health check failed", e);
            return Health.down()
                    .withDetail("camundaUrl", camundaUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
