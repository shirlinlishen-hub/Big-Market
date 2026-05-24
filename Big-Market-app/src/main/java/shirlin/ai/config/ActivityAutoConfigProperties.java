package shirlin.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
@Data
@ConfigurationProperties(prefix = "activity.auto-config")
public class ActivityAutoConfigProperties {

    private boolean enabled = false;
    private List<Long> activityIds;
    private List<Long> strategyIds;
}