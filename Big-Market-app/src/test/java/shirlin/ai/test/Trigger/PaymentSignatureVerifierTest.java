package shirlin.ai.test.Trigger;

import org.junit.jupiter.api.Test;
import shirlin.ai.trigger.security.PaymentSignatureVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentSignatureVerifierTest {
    private static final String SECRET = "test-payment-secret-with-enough-length";
    private static final long NOW = 1_800_000_000L;

    @Test
    void acceptsMatchingBodyWithinTimestampWindow() {
        PaymentSignatureVerifier verifier = new PaymentSignatureVerifier(
                SECRET, 300, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        String body = "{\"eventId\":\"evt-1\"}";
        String signature = verifier.signForTest(Long.toString(NOW), body);

        assertDoesNotThrow(() -> verifier.verify(Long.toString(NOW), signature, body));
    }

    @Test
    void rejectsTamperedBodyAndExpiredTimestamp() {
        PaymentSignatureVerifier verifier = new PaymentSignatureVerifier(
                SECRET, 300, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        String signature = verifier.signForTest(Long.toString(NOW), "{}");

        assertThrows(SecurityException.class,
                () -> verifier.verify(Long.toString(NOW), signature, "{\"changed\":true}"));
        assertThrows(SecurityException.class,
                () -> verifier.verify(Long.toString(NOW - 301), signature, "{}"));
    }
}
