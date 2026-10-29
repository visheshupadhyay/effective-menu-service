package com.vishesh.effectivemenu.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        KafkaConsumerErrorConfiguration.class,
        KafkaDeadLetterConfiguration.class,
        KafkaConsumerErrorConfigurationTest.TestBeans.class
})
@TestPropertySource(properties =
        "app.kafka.menu-item-changed-dlt=test-menu-item-changed.DLT")
public class KafkaConsumerErrorConfigurationTest {

    @Autowired
    DefaultErrorHandler errorHandler;

    @Autowired
    DeadLetterPublishingRecoverer recoverer;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void createsKafkaErrorHandlingBeans() {
        assertNotNull(errorHandler);
        assertNotNull(recoverer);
    }

    @Test
    void registersKafkaTemplateAsDependencyForDeadLetterRecoverer() {
        KafkaTemplate<String, Object> kafkaTemplate =
                applicationContext.getBean(KafkaTemplate.class);

        assertNotNull(kafkaTemplate);
        assertSame(recoverer, applicationContext.getBean(DeadLetterPublishingRecoverer.class));
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }
}
