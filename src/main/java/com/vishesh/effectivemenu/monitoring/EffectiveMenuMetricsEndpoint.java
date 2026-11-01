package com.vishesh.effectivemenu.monitoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the current effective-menu counters through an explicit HTTP route. */
@RestController
@RequestMapping("/actuator/effective-menu")
public class EffectiveMenuMetricsEndpoint {
    private final EffectiveMenuOperationalMetrics metrics;

    public EffectiveMenuMetricsEndpoint(EffectiveMenuOperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public EffectiveMenuMetricsSnapshot read() {
        return metrics.snapshot();
    }
}
