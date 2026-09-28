package shirlin.ai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
