package shirlin.ai.test.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import shirlin.ai.config.DeliveryModeProperties;
import shirlin.ai.infrastructure.adapter.repository.AwardDeliveryService;
import shirlin.ai.infrastructure.adapter.repository.TransactionalOutboxService;
import shirlin.ai.infrastructure.dao.IAwardDeliveryAuditDao;
import shirlin.ai.infrastructure.dao.IAwardDeliveryTaskDao;
import shirlin.ai.infrastructure.dao.IOutboxEventDao;
import shirlin.ai.infrastructure.dao.IUserAwardRecordDao;
import shirlin.ai.infrastructure.delivery.AwardDeliveryApplicationService;
import shirlin.ai.infrastructure.delivery.AwardFulfillmentService;
import shirlin.ai.infrastructure.delivery.DeliveryFailureRecorder;
import shirlin.ai.infrastructure.messaging.OutboxPublishService;
import shirlin.ai.job.AwardDeliveryJob;
import shirlin.ai.job.OutboxPublishJob;
import shirlin.ai.messaging.AwardDeliveryConsumer;
import shirlin.ai.messaging.AwardDeliveryDeadLetterConsumer;
import shirlin.ai.types.Tool.SnowflakeIdGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeliveryModeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryModeTestConfiguration.class);

    @Test
    void localEnablesOnlyLocalDelivery() {
        runner.withPropertyValues("big-market.delivery.mode=local").run(context -> {
            assertThat(context).hasSingleBean(AwardDeliveryJob.class);
            assertThat(context).doesNotHaveBean(OutboxPublishJob.class);
            assertThat(context).doesNotHaveBean(AwardDeliveryConsumer.class);
            assertThat(context).doesNotHaveBean(AwardDeliveryDeadLetterConsumer.class);
        });
    }

    @Test
    void mqPreparePublishesWhileLocalDeliveryRemainsActive() {
        runner.withPropertyValues("big-market.delivery.mode=mq-prepare").run(context -> {
            assertThat(context).hasSingleBean(AwardDeliveryJob.class);
            assertThat(context).hasSingleBean(OutboxPublishJob.class);
            assertThat(context).doesNotHaveBean(AwardDeliveryConsumer.class);
            assertThat(context).doesNotHaveBean(AwardDeliveryDeadLetterConsumer.class);
        });
    }

    @Test
    void mqEnablesPublisherAndConsumersOnly() {
        runner.withPropertyValues("big-market.delivery.mode=mq").run(context -> {
            assertThat(context).doesNotHaveBean(AwardDeliveryJob.class);
            assertThat(context).hasSingleBean(OutboxPublishJob.class);
            assertThat(context).hasSingleBean(AwardDeliveryConsumer.class);
            assertThat(context).hasSingleBean(AwardDeliveryDeadLetterConsumer.class);
        });
    }

    @Test
    void missingModeDefaultsToLocal() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AwardDeliveryJob.class);
            assertThat(context).doesNotHaveBean(OutboxPublishJob.class);
        });
    }

    @Test
    void unknownModeFailsStartup() {
        runner.withPropertyValues("big-market.delivery.mode=unknown").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("big-market.delivery");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DeliveryModeProperties.class)
    @Import({AwardDeliveryJob.class, OutboxPublishJob.class,
            AwardDeliveryConsumer.class, AwardDeliveryDeadLetterConsumer.class})
    static class DeliveryModeTestConfiguration {
        @Bean AwardDeliveryService awardDeliveryService() {
            return mock(AwardDeliveryService.class);
        }

        @Bean IAwardDeliveryTaskDao awardDeliveryTaskDao() {
            return mock(IAwardDeliveryTaskDao.class);
        }

        @Bean IAwardDeliveryAuditDao awardDeliveryAuditDao() {
            return mock(IAwardDeliveryAuditDao.class);
        }

        @Bean IUserAwardRecordDao userAwardRecordDao() {
            return mock(IUserAwardRecordDao.class);
        }

        @Bean AwardFulfillmentService awardFulfillmentService() {
            return mock(AwardFulfillmentService.class);
        }

        @Bean TransactionalOutboxService transactionalOutboxService() {
            return mock(TransactionalOutboxService.class);
        }

        @Bean IOutboxEventDao outboxEventDao() {
            return mock(IOutboxEventDao.class);
        }

        @Bean SnowflakeIdGenerator snowflakeIdGenerator() {
            return mock(SnowflakeIdGenerator.class);
        }

        @Bean OutboxPublishService outboxPublishService() {
            return mock(OutboxPublishService.class);
        }

        @Bean AwardDeliveryApplicationService awardDeliveryApplicationService() {
            return mock(AwardDeliveryApplicationService.class);
        }

        @Bean DeliveryFailureRecorder deliveryFailureRecorder() {
            return mock(DeliveryFailureRecorder.class);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
