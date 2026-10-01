package shirlin.ai.trigger.security;

import java.util.Set;

public record JwtIdentity(String userId, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
