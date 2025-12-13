package com.pesatalk.controller;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final ApplicationAvailability applicationAvailability;
    private final Instant startTime;

    public HealthController(ApplicationAvailability applicationAvailability) {
        this.applicationAvailability = applicationAvailability;
        this.startTime = Instant.now();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        LivenessState livenessState = applicationAvailability.getLivenessState();
        ReadinessState readinessState = applicationAvailability.getReadinessState();

        boolean isHealthy = livenessState == LivenessState.CORRECT
            && readinessState == ReadinessState.ACCEPTING_TRAFFIC;

        Map<String, Object> response = Map.of(
            "status", isHealthy ? "UP" : "DOWN",
            "liveness", livenessState.name(),
            "readiness", readinessState.name(),
            "uptime", java.time.Duration.between(startTime, Instant.now()).toString()
        );

        return isHealthy
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        ReadinessState readinessState = applicationAvailability.getReadinessState();

        if (readinessState == ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.ok(Map.of("status", "READY"));
        }

        return ResponseEntity.status(503)
            .body(Map.of("status", "NOT_READY"));
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        LivenessState livenessState = applicationAvailability.getLivenessState();

        if (livenessState == LivenessState.CORRECT) {
            return ResponseEntity.ok(Map.of("status", "ALIVE"));
        }

        return ResponseEntity.status(503)
            .body(Map.of("status", "BROKEN"));
    }
}
