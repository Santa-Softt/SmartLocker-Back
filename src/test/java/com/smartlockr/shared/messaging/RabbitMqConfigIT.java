package com.smartlockr.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlockr.billing.application.messaging.PaymentWebhookReceivedMessage;
import com.smartlockr.shared.properties.MessagingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RabbitMqConfigIT.TestApplication.class)
class RabbitMqConfigIT {

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerRabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MessagingProperties properties;

    @Test
    @DisplayName("RabbitMQ config routes payment webhook messages to durable queue")
    void shouldRoutePaymentWebhookMessageToQueue() {
        rabbitAdmin.purgeQueue(properties.rabbit().queues().paymentWebhook());

        var event = new PaymentWebhookReceivedMessage(MessageMetadata.create("request-1"), "12345", "request-1");
        rabbitTemplate.convertAndSend(
                properties.rabbit().exchange(),
                properties.rabbit().routingKeys().paymentWebhook(),
                event
        );

        Object received = rabbitTemplate.receiveAndConvert(properties.rabbit().queues().paymentWebhook(), 5_000);

        assertThat(received).isInstanceOf(PaymentWebhookReceivedMessage.class);
        assertThat(((PaymentWebhookReceivedMessage) received).paymentId()).isEqualTo("12345");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(MessagingProperties.class)
    @Import(RabbitMqConfig.class)
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
