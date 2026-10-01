-- Apply to the single database before enabling SKU purchases.
-- Check and resolve pre-existing duplicate (user_id, out_business_no) pairs first.
SELECT user_id, out_business_no, COUNT(*) AS copies
FROM activity_order
WHERE out_business_no IS NOT NULL
GROUP BY user_id, out_business_no
HAVING COUNT(*) > 1;

ALTER TABLE activity_order
    ADD UNIQUE KEY uk_user_business_no (user_id, out_business_no);

-- New orders use status 3 = purchase granted. Existing 0/1/2 rows retain their meaning.
