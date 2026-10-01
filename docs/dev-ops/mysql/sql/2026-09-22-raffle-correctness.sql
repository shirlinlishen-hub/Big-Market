-- Apply once in the single database before deploying strict strategy validation.
-- Resolve any duplicate strategy/model pairs before creating the unique key.
SELECT strategy_id, rule_model, COUNT(*) AS copies
FROM strategy_rule GROUP BY strategy_id, rule_model HAVING COUNT(*) > 1;

ALTER TABLE strategy_rule
    ADD UNIQUE KEY uk_strategy_rule_model (strategy_id, rule_model);

-- The seeded strategy uses award 106 as its unlimited-stock fallback.
INSERT IGNORE INTO strategy_rule (strategy_id, rule_type, rule_model, rule_value, rule_desc,
                           create_time, update_time)
VALUES (100001, 1, 'rule_fallback', '{"award_id":106}', 'Explicit fallback award', NOW(), NOW());

-- Existing demo data labels draw-count thresholds as total spend. Use 50/10
-- completed draws for the two sample groups; review these thresholds before launch.
UPDATE strategy_rule
SET rule_value = JSON_SET(rule_value,
        '$.threshold_key', 'draw_count',
        '$.groups[0].threshold_value', 50,
        '$.groups[0].group_id', 'weight_group_50',
        '$.groups[0].desc', '累计抽奖>=50次',
        '$.groups[1].threshold_value', 10,
        '$.groups[1].group_id', 'weight_group_10',
        '$.groups[1].desc', '累计抽奖>=10次'),
    rule_desc = '权重规则: 根据用户累计抽奖次数选择不同概率奖池',
    update_time = NOW()
WHERE strategy_id = 100001 AND rule_model = 'rule_weight'
  AND JSON_UNQUOTE(JSON_EXTRACT(rule_value, '$.threshold_key')) = 'total_spend';
