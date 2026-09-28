package shirlin.ai.infrastructure.messaging.exception;

public class RetryableDeliveryException extends RuntimeException {
    public RetryableDeliveryException(String message) {
        super(message);
    }

    public RetryableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
