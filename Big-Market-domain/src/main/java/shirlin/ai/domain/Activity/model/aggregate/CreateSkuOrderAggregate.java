package shirlin.ai.domain.Activity.model.aggregate;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.model.entity.ActivityFactorEntity;
import shirlin.ai.domain.Activity.model.entity.ActivitySkuEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSkuOrderAggregate {

    ActivityFactorEntity factor;
    ActivitySkuEntity sku;
    ActivityEntity  activity;

}
