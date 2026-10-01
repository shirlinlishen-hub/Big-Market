package shirlin.ai.domain.Activity.service.Quota;

/** Purchase grants the total balance; daily and monthly limits cap its use. */
public final class DrawQuotaPolicy {

    private DrawQuotaPolicy() {
    }

    public static int available(int totalBalance, int dayLimit, int dayUsed,
                                int monthLimit, int monthUsed) {
        if (totalBalance < -1 || dayLimit < -1 || monthLimit < -1
                || dayUsed < 0 || monthUsed < 0) {
            throw new IllegalArgumentException("Invalid draw quota");
        }
        int totalAvailable = totalBalance == -1 ? Integer.MAX_VALUE : totalBalance;
        int dayAvailable = dayLimit == -1 ? Integer.MAX_VALUE : Math.max(0, dayLimit - dayUsed);
        int monthAvailable = monthLimit == -1 ? Integer.MAX_VALUE : Math.max(0, monthLimit - monthUsed);
        return Math.min(totalAvailable, Math.min(dayAvailable, monthAvailable));
    }
}
