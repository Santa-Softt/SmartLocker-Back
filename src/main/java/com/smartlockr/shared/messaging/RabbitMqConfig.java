package com.smartlockr.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlockr.shared.properties.MessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private static final String DEAD_SUFFIX = ".dead";

    private final MessagingProperties properties;

    @Bean
    TopicExchange smartlockrExchange() {
        return new TopicExchange(properties.rabbit().exchange(), true, false);
    }

    @Bean
    DirectExchange smartlockrDeadLetterExchange() {
        return new DirectExchange(properties.rabbit().deadLetterExchange(), true, false);
    }

    @Bean
    Queue paymentWebhookQueue() {
        return durableQueue(properties.rabbit().queues().paymentWebhook(), properties.rabbit().routingKeys().paymentWebhook());
    }

    @Bean
    Queue welcomeEmailQueue() {
        return durableQueue(properties.rabbit().queues().welcomeEmail(), properties.rabbit().routingKeys().welcomeEmail());
    }

    @Bean
    Queue paymentReceiptQueue() {
        return durableQueue(properties.rabbit().queues().paymentReceipt(), properties.rabbit().routingKeys().paymentReceipt());
    }

    @Bean
    Queue paymentWebhookDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName(properties.rabbit().queues().paymentWebhook())).build();
    }

    @Bean
    Queue welcomeEmailDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName(properties.rabbit().queues().welcomeEmail())).build();
    }

    @Bean
    Queue paymentReceiptDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName(properties.rabbit().queues().paymentReceipt())).build();
    }

    @Bean
    Binding paymentWebhookBinding(Queue paymentWebhookQueue, TopicExchange smartlockrExchange) {
        return BindingBuilder.bind(paymentWebhookQueue)
                .to(smartlockrExchange)
                .with(properties.rabbit().routingKeys().paymentWebhook());
    }

    @Bean
    Binding welcomeEmailBinding(Queue welcomeEmailQueue, TopicExchange smartlockrExchange) {
        return BindingBuilder.bind(welcomeEmailQueue)
                .to(smartlockrExchange)
                .with(properties.rabbit().routingKeys().welcomeEmail());
    }

    @Bean
    Binding paymentReceiptBinding(Queue paymentReceiptQueue, TopicExchange smartlockrExchange) {
        return BindingBuilder.bind(paymentReceiptQueue)
                .to(smartlockrExchange)
                .with(properties.rabbit().routingKeys().paymentReceipt());
    }

    @Bean
    Binding paymentWebhookDeadLetterBinding(Queue paymentWebhookDeadLetterQueue, DirectExchange smartlockrDeadLetterExchange) {
        return BindingBuilder.bind(paymentWebhookDeadLetterQueue)
                .to(smartlockrDeadLetterExchange)
                .with(deadLetterRoutingKey(properties.rabbit().routingKeys().paymentWebhook()));
    }

    @Bean
    Binding welcomeEmailDeadLetterBinding(Queue welcomeEmailDeadLetterQueue, DirectExchange smartlockrDeadLetterExchange) {
        return BindingBuilder.bind(welcomeEmailDeadLetterQueue)
                .to(smartlockrDeadLetterExchange)
                .with(deadLetterRoutingKey(properties.rabbit().routingKeys().welcomeEmail()));
    }

    @Bean
    Binding paymentReceiptDeadLetterBinding(Queue paymentReceiptDeadLetterQueue, DirectExchange smartlockrDeadLetterExchange) {
        return BindingBuilder.bind(paymentReceiptDeadLetterQueue)
                .to(smartlockrDeadLetterExchange)
                .with(deadLetterRoutingKey(properties.rabbit().routingKeys().paymentReceipt()));
    }

    @Bean
    MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    private Queue durableQueue(String queueName, String routingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(properties.rabbit().deadLetterExchange())
                .deadLetterRoutingKey(deadLetterRoutingKey(routingKey))
                .build();
    }

    private String deadLetterQueueName(String queueName) {
        return queueName + ".dlq";
    }

    private String deadLetterRoutingKey(String routingKey) {
        return routingKey + DEAD_SUFFIX;
    }
}
