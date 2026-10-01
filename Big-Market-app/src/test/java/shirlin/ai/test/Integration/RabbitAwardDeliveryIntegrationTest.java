package shirlin.ai.test.Integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import shirlin.ai.config.RabbitMQConfig;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IInboxEventDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.dao.IUserPointsDao;
import shirlin.ai.infrastructure.delivery.AwardDeliveryApplicationService;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentHandler;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;
import shirlin.ai.infrastructure.delivery.DeliveryFailureRecorder;
import shirlin.ai.infrastructure.delivery.ManualAwardFulfillmentHandler;
import shirlin.ai.infrastructure.delivery.PointsAwardFulfillmentHandler;
import shirlin.ai.infrastructure.messaging.model.AwardDeliveryRequestedPayload;
import shirlin.ai.infrastructure.messaging.model.DomainEventEnvelope;
import shirlin.ai.messaging.AwardDeliveryConsumer;
import shirlin.ai.messaging.AwardDeliveryDeadLetterConsumer;
import shirlin.ai.messaging.RabbitMessagePublisher;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(RabbitAwardDeliveryIntegrationTest.IntegrationConfiguration.class)
@EnabledIfEnvironmentVariable(named = "BIG_MARKET_REAL_INTEGRATION", matches = "(?i)true")
class RabbitAwardDeliveryIntegrationTest {
    private static final String ID_PREFIX = "itmq-";

    @jakarta.annotation.Resource private JdbcTemplate jdbc;
    @jakarta.annotation.Resource private RabbitAdmin rabbitAdmin;
    @jakarta.annotation.Resource private RabbitTemplate rabbitTemplate;
    @jakarta.annotation.Resource private RabbitMessagePublisher publisher;
    @jakarta.annotation.Resource private AwardDeliveryConsumer consumer;
    @jakarta.annotation.Resource private AwardDeliveryDeadLetterConsumer deadLetterConsumer;
    @jakarta.annotation.Resource private ObjectMapper objectMapper;

    @BeforeAll
    static void verifyEnvironment(
            @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Autowired RabbitAdmin rabbitAdmin) {
        Integer dispatchVersionColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'award_delivery_task'
                  AND column_name = 'dispatch_version'
                """, Integer.class);
        assertEquals(1, dispatchVersionColumns,
                "Run the 2026-09-28 MySQL migration before this integration test");

        RabbitMQConfig topology = new RabbitMQConfig();
        rabbitAdmin.declareExchange(topology.eventExchange());
        rabbitAdmin.declareExchange(topology.deadExchange());
        rabbitAdmin.declareQueue(topology.deliveryQueue());
        rabbitAdmin.declareQueue(topology.deadQueue());
        rabbitAdmin.declareBinding(topology.deliveryBinding(
                topology.deliveryQueue(), topology.eventExchange()));
        rabbitAdmin.declareBinding(topology.deadBinding(
                topology.deadQueue(), topology.deadExchange()));
    }

    @BeforeEach
    void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.DELIVERY_QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMQConfig.DEAD_QUEUE, true);
    }

    @AfterEach
    void cleanIntegrationRows() {
        jdbc.update("DELETE FROM award_delivery_audit WHERE order_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM inbox_event WHERE aggregate_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM outbox_event WHERE aggregate_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM user_points_ledger WHERE order_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM award_delivery_task WHERE order_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM user_award_record WHERE raffle_order_id LIKE ?", ID_PREFIX + "%");
        jdbc.update("DELETE FROM user_points_account WHERE user_id LIKE ?", ID_PREFIX + "%");
    }

    @Test
    void duplicatePublishOfSameEventProducesOneInboxAndOneCredit() {
        Scenario scenario = prepare(10);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event = scenario.event("event-a", 0);

        publishAndConsume(event);
        publishAndConsume(event);

        assertEquals(1, count("inbox_event", "aggregate_id", scenario.orderId()));
        assertEquals(1, count("user_points_ledger", "order_id", scenario.orderId()));
        assertEquals(10L, points(scenario.userId()));
    }

    @Test
    void confirmedPublishCanBeRepeatedWhenOutboxMarkWasSkipped() {
        Scenario scenario = prepare(11);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event = scenario.event("event-b", 0);
        jdbc.update("""
                INSERT INTO outbox_event
                    (event_id, event_type, event_key, aggregate_id, partition_key, payload, status)
                VALUES (?, 'AWARD_DELIVERY_REQUESTED', ?, ?, ?, JSON_OBJECT('orderId', ?, 'userId', ?), 0)
                """, event.eventId(), event.eventKey(), scenario.orderId(), scenario.userId(),
                scenario.orderId(), scenario.userId());

        publishAndConsume(event);
        publishAndConsume(event);

        assertEquals(0, scalar("SELECT status FROM outbox_event WHERE event_id = ?",
                event.eventId()));
        assertEquals(1, count("user_points_ledger", "order_id", scenario.orderId()));
        assertEquals(11L, points(scenario.userId()));
    }

    @Test
    void replayAfterConsumerCommitBeforeAckDoesNotCreditAgain() {
        Scenario scenario = prepare(12);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event = scenario.event("event-c", 0);
        publisher.publish(event);
        Message delivery = receive(RabbitMQConfig.DELIVERY_QUEUE);

        consumer.consume(delivery);
        consumer.consume(delivery);

        assertEquals(1, count("user_points_ledger", "order_id", scenario.orderId()));
        assertEquals(12L, points(scenario.userId()));
    }

    @Test
    void conflictingReplayIsDeadLetteredWithoutChangingAccount() throws Exception {
        Scenario scenario = prepare(13);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> accepted = scenario.event("event-d", 0);
        publishAndConsume(accepted);
        DomainEventEnvelope<AwardDeliveryRequestedPayload> conflicting =
                scenario.event("event-d", 1);

        Message conflict = message(conflicting);
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consume(conflict));
        rabbitTemplate.send(RabbitMQConfig.DEAD_EXCHANGE,
                RabbitMQConfig.DEAD_ROUTE, conflict);
        deadLetterConsumer.consume(receive(RabbitMQConfig.DEAD_QUEUE));

        assertEquals(13L, points(scenario.userId()));
        assertEquals(1, count("user_points_ledger", "order_id", scenario.orderId()));
        assertEquals(2, scalar("SELECT status FROM award_delivery_task WHERE order_id = ?",
                scenario.orderId()));
    }

    @Test
    void differentManualRetryEventsForOneOrderStillCreditOnce() {
        Scenario scenario = prepare(14);

        publishAndConsume(scenario.event("event-e1", 1));
        publishAndConsume(scenario.event("event-e2", 2));

        assertEquals(2, count("inbox_event", "aggregate_id", scenario.orderId()));
        assertEquals(1, count("user_points_ledger", "order_id", scenario.orderId()));
        assertEquals(14L, points(scenario.userId()));
    }

    private Scenario prepare(int points) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String orderId = ID_PREFIX + "order-" + suffix;
        String userId = ID_PREFIX + "user-" + suffix;
        jdbc.update("""
                INSERT INTO user_award_record
                    (user_id, strategy_id, raffle_order_id, award_id, award_type,
                     award_content, award_state, draw_time)
                VALUES (?, 100001, ?, 1, 4, ?, 0, NOW())
                """, userId, orderId, points + " points");
        jdbc.update("""
                INSERT INTO award_delivery_task
                    (order_id, user_id, award_id, award_type, award_key, award_value,
                     status, dispatch_version)
                VALUES (?, ?, 1, 4, 'user_points', ?, 0, 0)
                """, orderId, userId, Integer.toString(points));
        return new Scenario(orderId, userId);
    }

    private void publishAndConsume(DomainEventEnvelope<AwardDeliveryRequestedPayload> event) {
        publisher.publish(event);
        consumer.consume(receive(RabbitMQConfig.DELIVERY_QUEUE));
    }

    private Message receive(String queue) {
        Message message = rabbitTemplate.receive(queue, 5_000);
        assertNotNull(message, "RabbitMQ did not deliver a message from " + queue);
        return message;
    }

    private Message message(DomainEventEnvelope<AwardDeliveryRequestedPayload> event)
            throws Exception {
        return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.eventId())
                .setCorrelationId(event.aggregateId())
                .build();
    }

    private int count(String table, String column, String value) {
        return scalar("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", value);
    }

    private int scalar(String sql, String value) {
        Integer result = jdbc.queryForObject(sql, Integer.class, value);
        return result == null ? 0 : result;
    }

    private long points(String userId) {
        Long result = jdbc.queryForObject(
                "SELECT points FROM user_points_account WHERE user_id = ?", Long.class, userId);
        return result == null ? 0 : result;
    }

    private record Scenario(String orderId, String userId) {
        DomainEventEnvelope<AwardDeliveryRequestedPayload> event(String eventSuffix, int version) {
            String eventId = ID_PREFIX + eventSuffix + "-" + orderId.substring(orderId.length() - 12);
            return new DomainEventEnvelope<>(eventId, "AWARD_DELIVERY_REQUESTED",
                    "award-delivery:" + orderId + ":v" + version, 1,
                    orderId, userId, Instant.now(),
                    new AwardDeliveryRequestedPayload(orderId, userId));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("shirlin.ai.infrastructure.dao")
    static class IntegrationConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(required("BIG_MARKET_JDBC_URL"));
            dataSource.setUsername(required("BIG_MARKET_DB_USER"));
            dataSource.setPassword(required("BIG_MARKET_DB_PASSWORD"));
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath*:/mybatis/mapper/*.xml"));
            return factory.getObject();
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean PointsAwardFulfillmentHandler pointsHandler(IUserPointsDao pointsDao) {
            return new PointsAwardFulfillmentHandler(pointsDao);
        }

        @Bean ManualAwardFulfillmentHandler manualHandler() {
            return new ManualAwardFulfillmentHandler();
        }

        @Bean AwardFulfillmentService fulfillmentService(
                IAwardDeliveryTaskDao taskDao,
                IUserAwardRecordDao awardRecordDao,
                List<AwardFulfillmentHandler> handlers) {
            return new AwardFulfillmentService(taskDao, awardRecordDao, handlers);
        }

        @Bean AwardDeliveryApplicationService deliveryApplicationService(
                IInboxEventDao inboxDao, AwardFulfillmentService fulfillmentService,
                ObjectMapper objectMapper) {
            return new AwardDeliveryApplicationService(inboxDao, fulfillmentService, objectMapper);
        }

        @Bean DeliveryFailureRecorder failureRecorder(IAwardDeliveryTaskDao taskDao) {
            return new DeliveryFailureRecorder(taskDao);
        }

        @Bean AwardDeliveryConsumer consumer(
                AwardDeliveryApplicationService deliveryService,
                DeliveryFailureRecorder failureRecorder, ObjectMapper objectMapper) {
            return new AwardDeliveryConsumer(deliveryService, failureRecorder, objectMapper);
        }

        @Bean AwardDeliveryDeadLetterConsumer deadLetterConsumer(
                IAwardDeliveryTaskDao taskDao, IAwardDeliveryAuditDao auditDao,
                ObjectMapper objectMapper) {
            return new AwardDeliveryDeadLetterConsumer(taskDao, auditDao, objectMapper);
        }

        @Bean CachingConnectionFactory rabbitConnectionFactory() {
            CachingConnectionFactory factory = new CachingConnectionFactory(
                    env("BIG_MARKET_RABBIT_HOST", "localhost"),
                    Integer.parseInt(env("BIG_MARKET_RABBIT_PORT", "5672")));
            factory.setUsername(env("BIG_MARKET_RABBIT_USERNAME", "guest"));
            factory.setPassword(env("BIG_MARKET_RABBIT_PASSWORD", "guest"));
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            return factory;
        }

        @Bean RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
            RabbitTemplate template = new RabbitTemplate(connectionFactory);
            template.setMandatory(true);
            return template;
        }

        @Bean RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        @Bean RabbitMessagePublisher publisher(
                RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
            return new RabbitMessagePublisher(rabbitTemplate, objectMapper);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " is required when real integration is enabled");
            }
            return value;
        }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
