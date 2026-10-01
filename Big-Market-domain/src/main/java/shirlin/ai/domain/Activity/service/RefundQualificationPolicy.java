package shirlin.ai.domain.Activity.service;

/** Calculates the reversible part of a finite purchase entitlement. */
public final class RefundQualificationPolicy {
    private RefundQualificationPolicy() { }

    public static Decision decide(int purchaseGrant, int completedRebateGrant, int currentSurplus) {
        if (purchaseGrant < 0 || completedRebateGrant < 0) {
            throw new IllegalArgumentException("Unlimited qualification requires manual review");
        }
        if (currentSurplus < 0) {
            throw new IllegalArgumentException("Unlimited account requires manual review");
        }
        int granted = Math.addExact(purchaseGrant, completedRebateGrant);
        int removable = Math.min(granted, currentSurplus);
        return new Decision(granted, removable, granted - removable);
    }

    public record Decision(int grantedCount, int removedUnusedCount, int consumedExposureCount) { }
}
