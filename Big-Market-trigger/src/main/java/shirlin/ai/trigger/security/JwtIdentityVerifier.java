package shirlin.ai.trigger.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtIdentityVerifier {
    private final JWTVerifier verifier;

    public JwtIdentityVerifier(
            @Value("${big-market.security.user-jwt-secret:}") String secret,
            @Value("${big-market.security.user-jwt-issuer:big-market}") String issuer) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("BIG_MARKET_USER_JWT_SECRET must contain at least 32 characters");
        }
        this.verifier = JWT.require(Algorithm.HMAC256(secret)).withIssuer(issuer).build();
    }

    public JwtIdentity verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("Bearer token is required");
        }
        try {
            DecodedJWT jwt = verifier.verify(authorization.substring(7));
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) throw new SecurityException("JWT subject is required");
            if (jwt.getExpiresAt() == null) throw new SecurityException("JWT expiration is required");
            List<String> roles = jwt.getClaim("roles").asList(String.class);
            return new JwtIdentity(subject, roles == null ? Set.of() : new HashSet<>(roles));
        } catch (SecurityException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SecurityException("Invalid bearer token", e);
        }
    }
}
