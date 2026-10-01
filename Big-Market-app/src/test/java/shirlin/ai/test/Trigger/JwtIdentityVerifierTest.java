package shirlin.ai.test.Trigger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import shirlin.ai.trigger.security.JwtIdentity;
import shirlin.ai.trigger.security.JwtIdentityVerifier;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtIdentityVerifierTest {
    @Test
    void userIdAndRolesComeOnlyFromVerifiedToken() {
        String secret = "test-user-jwt-secret-with-enough-length";
        String token = JWT.create().withIssuer("big-market-test").withSubject("user-1")
                .withClaim("roles", List.of("operations"))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
                .sign(Algorithm.HMAC256(secret));
        JwtIdentityVerifier verifier = new JwtIdentityVerifier(secret, "big-market-test");

        JwtIdentity identity = verifier.verify("Bearer " + token);

        assertEquals("user-1", identity.userId());
        assertEquals(true, identity.hasRole("operations"));
        assertThrows(SecurityException.class, () -> verifier.verify("Bearer " + token + "x"));
        String noExpiry = JWT.create().withIssuer("big-market-test").withSubject("user-1")
                .sign(Algorithm.HMAC256(secret));
        assertThrows(SecurityException.class, () -> verifier.verify("Bearer " + noExpiry));
    }
}
