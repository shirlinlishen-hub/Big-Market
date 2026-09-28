package shirlin.ai.infrastructure.messaging.exception;

public class NonRetryableDeliveryException extends RuntimeException {
    public NonRetryableDeliveryException(String message) {
        super(message);
    }

    public NonRetryableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
