package shirlin.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "big-market.delivery")
public class DeliveryModeProperties {
    private DeliveryMode mode = DeliveryMode.LOCAL;
    private long publisherDelayMs = 500;
    private int publisherBatchSize = 100;
    private long outboxLeaseSeconds = 30;

    public DeliveryMode getMode() {
        return mode;
    }

    public void setMode(DeliveryMode mode) {
        this.mode = mode;
    }

    public long getPublisherDelayMs() {
        return publisherDelayMs;
    }

    public void setPublisherDelayMs(long publisherDelayMs) {
        this.publisherDelayMs = publisherDelayMs;
    }

    public int getPublisherBatchSize() {
        return publisherBatchSize;
    }

    public void setPublisherBatchSize(int publisherBatchSize) {
        this.publisherBatchSize = publisherBatchSize;
    }

    public long getOutboxLeaseSeconds() {
        return outboxLeaseSeconds;
    }

    public void setOutboxLeaseSeconds(long outboxLeaseSeconds) {
        this.outboxLeaseSeconds = outboxLeaseSeconds;
    }
}
