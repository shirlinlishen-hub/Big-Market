package shirlin.ai.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import shirlin.ai.infrastructure.messaging.exception.NonRetryableDeliveryException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    public static final String EVENT_EXCHANGE = "big.market.events.v1";
    public static final String DELIVERY_QUEUE = "big.market.award.delivery.v1";
    public static final String DELIVERY_ROUTE = "award.delivery.requested.v1";
    public static final String DEAD_EXCHANGE = "big.market.dead.v1";
    public static final String DEAD_QUEUE = "big.market.award.delivery.dead.v1";
    public static final String DEAD_ROUTE = "award.delivery.dead.v1";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadExchange() {
        return new TopicExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue deliveryQueue() {
        return QueueBuilder.durable(DELIVERY_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_ROUTE)
                .build();
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    public Binding deliveryBinding(
            @Qualifier("deliveryQueue") Queue queue,
            @Qualifier("eventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DELIVERY_ROUTE);
    }

    @Bean
    public Binding deadBinding(
            @Qualifier("deadQueue") Queue queue,
            @Qualifier("deadExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DEAD_ROUTE);
    }

    @Bean("awardDeliveryRetryTemplate")
    public RetryTemplate awardDeliveryRetryTemplate() {
        return createDeliveryRetryTemplate(1_000L, 30_000L);
    }

    @Bean("awardDeliveryListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory awardDeliveryListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("awardDeliveryRetryTemplate") RetryTemplate retryTemplate) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    public static RetryTemplate createDeliveryRetryTemplate(
            long initialInterval, long maxInterval) {
        ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxInterval);

        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(AmqpRejectAndDontRequeueException.class, false);
        retryable.put(NonRetryableDeliveryException.class, false);
        retryable.put(Exception.class, true);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(5, retryable, true, true));
        retryTemplate.setBackOffPolicy(backOff);
        return retryTemplate;
    }
}
