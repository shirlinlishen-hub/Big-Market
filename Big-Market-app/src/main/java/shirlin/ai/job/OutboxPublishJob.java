package shirlin.ai.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shirlin.ai.infrastructure.messaging.OutboxPublishService;

import java.net.InetAddress;
import java.time.Duration;

@Component
public class OutboxPublishJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishJob.class);

    private final OutboxPublishService publisher;
    private final String workerId;
    private final int batchSize;
    private final long leaseSeconds;

    public OutboxPublishJob(
            OutboxPublishService publisher,
            @Value("${spring.application.name:big-market}") String applicationName,
            @Value("${big-market.delivery.publisher-batch-size:100}") int batchSize,
            @Value("${big-market.delivery.outbox-lease-seconds:30}") long leaseSeconds) {
        this.publisher = publisher;
        this.workerId = workerId(applicationName);
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${big-market.delivery.publisher-delay-ms:500}")
    public void run() {
        int published = publisher.publishBatch(
                workerId, batchSize, Duration.ofSeconds(leaseSeconds));
        if (published > 0) {
            log.info("Published {} award delivery Outbox events, workerId={}",
                    published, workerId);
        }
    }

    private static String workerId(String applicationName) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            host = "unknown-host";
        }
        String value = applicationName + ":" + host + ":" + ProcessHandle.current().pid();
        return value.substring(0, Math.min(value.length(), 64));
    }
}
