package shirlin.ai.domain.strategy.service.Rule;

import shirlin.ai.domain.strategy.model.entity.RuleLockConfigEntity;
import shirlin.ai.domain.strategy.model.entity.RuleLuckConfigEntity;

/** Pure decisions for the single post-draw rule path. */
public final class PostDrawRulePolicy {
    private PostDrawRulePolicy() { }

    public static int choose(int candidateId, int fallbackId, RuleLockConfigEntity lock,
                             int completedDraws, RuleLuckConfigEntity luck, int currentLuck) {
        int selected = candidateId;
        if (lock != null && lock.getLockedAwardIds() != null
                && lock.getLockedAwardIds().contains(candidateId)
                && completedDraws < lock.getUnlockCount()) {
            selected = fallbackId;
        }
        if (luck != null && currentLuck >= luck.getLuckThreshold() - 1) {
            selected = luck.getLuckAwardId();
        }
        return selected;
    }

    public static int nextLuck(int finalAwardId, RuleLuckConfigEntity luck, int currentLuck) {
        if (luck == null) return currentLuck;
        if (finalAwardId == luck.getLuckAwardId()) return 0;
        return Math.addExact(currentLuck, luck.getLuckIncrement());
    }

    public static void validate(RuleLockConfigEntity lock, RuleLuckConfigEntity luck,
                                int fallbackId) {
        if (lock != null && (lock.getUnlockCount() <= 0 || lock.getLockedAwardIds() == null
                || lock.getLockedAwardIds().isEmpty()
                || lock.getLockedAwardIds().contains(fallbackId))) {
            throw new IllegalArgumentException("Invalid lock rule configuration");
        }
        if (luck != null && (luck.getLuckThreshold() <= 0 || luck.getLuckIncrement() <= 0
                || luck.getLuckAwardId() != fallbackId)) {
            throw new IllegalArgumentException("Luck award must be the configured fallback");
        }
    }
}
