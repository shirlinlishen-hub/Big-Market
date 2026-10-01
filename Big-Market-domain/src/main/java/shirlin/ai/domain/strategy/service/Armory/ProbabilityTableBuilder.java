package shirlin.ai.domain.strategy.service.Armory;

import shirlin.ai.domain.strategy.model.entity.AwardRateRange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builds exact, non-overlapping [start, end) ticket ranges. */
public final class ProbabilityTableBuilder {
    private ProbabilityTableBuilder() { }

    public static List<AwardRateRange> build(Map<Integer, BigDecimal> rates,
                                             int precision, boolean requireFull) {
        if (precision <= 0 || rates == null || rates.isEmpty()) {
            throw new IllegalArgumentException("Probability pool is empty or precision is invalid");
        }
        List<Map.Entry<Integer, BigDecimal>> entries = new ArrayList<>(rates.entrySet());
        if (entries.stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)) {
            throw new IllegalArgumentException("Award ID and probability are required");
        }
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        BigDecimal total = BigDecimal.ZERO;
        int cursor = 0;
        List<AwardRateRange> result = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : entries) {
            BigDecimal rate = entry.getValue();
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Probability must be between 0 and 1");
            }
            int width;
            try {
                width = rate.multiply(BigDecimal.valueOf(precision)).intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Probability cannot be represented at this precision", e);
            }
            total = total.add(rate);
            if (width == 0) continue;
            result.add(new AwardRateRange(entry.getKey(), cursor, cursor + width));
            cursor += width;
        }
        if (cursor == 0 || cursor > precision || (requireFull && total.compareTo(BigDecimal.ONE) != 0)) {
            throw new IllegalArgumentException("Probability pool total is invalid");
        }
        return List.copyOf(result);
    }

    public static Integer pick(List<AwardRateRange> ranges, int ticket) {
        if (ranges == null || ranges.isEmpty() || ticket < 0
                || ticket >= ranges.get(ranges.size() - 1).getRangeEnd()) {
            throw new IllegalArgumentException("Ticket is outside the probability pool");
        }
        int left = 0;
        int right = ranges.size() - 1;
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (ticket < ranges.get(mid).getRangeEnd()) right = mid;
            else left = mid + 1;
        }
        AwardRateRange range = ranges.get(left);
        if (ticket < range.getRangeStart()) throw new IllegalArgumentException("Probability range has a gap");
        return range.getAwardId();
    }
}
