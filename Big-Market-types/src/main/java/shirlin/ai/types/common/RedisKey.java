package shirlin.ai.types.common;

public class RedisKey {

    //Activity
    private static final String ACTIVITY_KEY = "big_market:activity:activity";
    //Sku库存
    private static final String SKU_STOCK_KEY       = "big_market:activity:sku:stock:";
    //Sku lock
    private static final String SKU_STOCK_LOCK_KEY  = "big_market:activity:sku:stock:lock:";
    //Sku的队列
    private static final String SKU_STOCK_QUEUE_KEY = "big_market:activity:sku:stock:queue";
    //月额度
    private static final String MONTHLY_QUOTA_KEY   = "big_market:user:quota:monthly:";
    //日额度
    private static final String DAILY_QUOTA_KEY     = "big_market:user:quota:daily:";


    //Strategy相关
    private static final String RANGE_TABLE_KEY        = "big_market:strategy:range_table:";
    private static final String PRECISION_KEY          = "big_market:strategy:precision:";
    private static final String MAX_AWARD_KEY          = "big_market:strategy:max_award:";
    private static final String SUB_RANGE_TABLE_KEY    = "big_market:strategy:sub_range_table:";
    private static final String AWARD_STOCK_KEY        = "big_market:strategy:award:stock:";


}
