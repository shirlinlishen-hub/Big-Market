package shirlin.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import shirlin.ai.infrastructure.dao.po.Award;

import java.util.concurrent.ThreadLocalRandom;

/** Resolves variable awards once, when the draw is committed. */
public final class AwardPayloadResolver {
    private AwardPayloadResolver() { }

    public static String resolve(Award award) {
        if (award == null || award.getAwardKey() == null) {
            throw new IllegalArgumentException("Award configuration is missing");
        }
        String key = award.getAwardKey();
        if (!"user_points".equals(key) && !"random_points".equals(key)) {
            return award.getAwardConfig() == null ? "" : award.getAwardConfig();
        }
        try {
            JSONObject config = JSON.parseObject(award.getAwardConfig());
            if (config == null) throw new IllegalArgumentException("Points configuration is missing");
            if ("user_points".equals(key)) {
                int points = config.getIntValue("points");
                if (points <= 0) throw new IllegalArgumentException("Points must be positive");
                return String.valueOf(points);
            }
            int min = config.getIntValue("min");
            int max = config.getIntValue("max");
            if (min <= 0 || max < min || max == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid random points range");
            }
            return String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("Invalid award configuration", e);
        }
    }
}
