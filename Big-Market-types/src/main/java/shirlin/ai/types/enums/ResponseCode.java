package shirlin.ai.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    STRATEGY_NOT_ACTIVE("0003", "活动未开始或已下线"),
    DRAW_COUNT_NOT_ENOUGH("0004", "抽奖次数不足"),
    ACTIVITY_EXPIRED("0005", "活动已结束"),
    USER_IN_BLACKLIST("0006", "用户已被禁止参与"),
    ACTIVITY_SKU_STOCK_EMPTY("0007", "活动库存已耗尽"),
    ACTIVITY_NOT_EXISTS("0008","活动未开始或已下线")

    ;

    private String code;
    private String info;

}
