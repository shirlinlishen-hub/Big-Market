package shirlin.ai.test.Domain;

import org.junit.jupiter.api.Test;
import shirlin.ai.domain.Activity.service.Quota.DrawQuotaPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DrawQuotaPolicyTest {

    @Test
    public void repeatedPurchasesIncreaseBalanceWithoutIncreasingDailyOrMonthlyCaps() {
        assertEquals(3, DrawQuotaPolicy.available(20, 3, 0, 6, 0));
        assertEquals(1, DrawQuotaPolicy.available(20, 3, 2, 6, 5));
    }

    @Test
    public void aNewDayRestoresDailyAllowanceButNotMonthlyUsage() {
        assertEquals(3, DrawQuotaPolicy.available(7, 3, 0, 6, 3));
        assertEquals(0, DrawQuotaPolicy.available(7, 3, 0, 6, 6));
    }

    @Test
    public void unlimitedTotalStillRespectsPeriodCaps() {
        assertEquals(2, DrawQuotaPolicy.available(-1, 3, 1, 6, 4));
    }
}
