package com.vishesh.effectivemenu.monitoring;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports whether the Kafka broker can answer a lightweight metadata request. */
@Component("kafkaHealthIndicator")
public class KafkaHealthIndicator implements HealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            int brokerCount = adminClient.describeCluster()
                    .nodes()
                    .get(10, TimeUnit.SECONDS)
                    .size();
            return Health.up().withDetail("brokerCount", brokerCount).build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }
}
