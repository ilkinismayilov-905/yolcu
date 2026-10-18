-- Coin rate 100 → 500 coin = 1 AZN. Recompute AZN of the seeded conversions (ConversionService.seedIfEmpty marks them
-- with a coin_tx SEED row ref 'seed-conv:<id>') and their ledger labels. Idempotent: recomputes from coins every time.
-- User-made conversions keep the AZN they were paid out at.
update coin_conversions set azn = round(coins / 500.0, 2)
where exists (select 1 from coin_tx t where t.reason = 'SEED' and t.ref_id = concat('seed-conv:', coin_conversions.id));

update coin_tx set label = concat('Banka çevirmə: ',
    replace(cast((select c.azn from coin_conversions c where concat('conv:', c.id) = coin_tx.ref_id) as varchar(20)), '.', ','), ' ₼')
where reason = 'CONVERSION'
  and exists (select 1 from coin_tx s where s.reason = 'SEED' and s.ref_id = concat('seed-', coin_tx.ref_id));
