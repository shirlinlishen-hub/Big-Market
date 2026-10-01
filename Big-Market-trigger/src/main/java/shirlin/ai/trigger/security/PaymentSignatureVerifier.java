package shirlin.ai.trigger.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

@Component
public class PaymentSignatureVerifier {
    private final byte[] secret;
    private final long toleranceSeconds;
    private final Clock clock;

    @Autowired
    public PaymentSignatureVerifier(
            @Value("${big-market.security.payment-webhook-secret:}") String secret,
            @Value("${big-market.security.payment-webhook-tolerance-seconds:300}") long toleranceSeconds) {
        this(secret, toleranceSeconds, Clock.systemUTC());
    }

    public PaymentSignatureVerifier(String secret, long toleranceSeconds, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("BIG_MARKET_PAYMENT_WEBHOOK_SECRET must contain at least 32 characters");
        }
        if (toleranceSeconds <= 0) throw new IllegalArgumentException("Signature tolerance must be positive");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.toleranceSeconds = toleranceSeconds;
        this.clock = clock;
    }

    public void verify(String timestamp, String signature, String rawBody) {
        final long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (RuntimeException e) {
            throw new SecurityException("Invalid payment timestamp", e);
        }
        if (Math.abs(clock.instant().getEpochSecond() - epochSeconds) > toleranceSeconds) {
            throw new SecurityException("Payment signature timestamp expired");
        }
        String expected = sign(timestamp, rawBody);
        if (signature == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Invalid payment signature");
        }
    }

    public String signForTest(String timestamp, String rawBody) {
        return sign(timestamp, rawBody);
    }

    private String sign(String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (timestamp + "\n" + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot calculate payment signature", e);
        }
    }
}
