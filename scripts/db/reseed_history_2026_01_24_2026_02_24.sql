begin;

delete from price_history
where recorded_at::date between date '2026-01-24' and date '2026-02-24';

with base_prices as (
    select distinct on (ph.marketplace_product_id)
           ph.marketplace_product_id,
           ph.product_id,
           ph.marketplace,
           ph.price as base_price
    from price_history ph
    where ph.recorded_at::date = date '2026-02-25'
    order by ph.marketplace_product_id, ph.recorded_at desc
),
products as (
    select bp.*,
           case when random() < 0.5 then 1 else -1 end as trend_dir
    from base_prices bp
),
all_days as (
    select p.marketplace_product_id,
           p.product_id,
           p.marketplace,
           p.base_price,
           p.trend_dir,
           d::date as day
    from products p
    cross join generate_series(date '2026-01-24', date '2026-02-24', interval '1 day') d
),
pick_special_days as (
    select marketplace_product_id,
           day
    from (
        select ad.marketplace_product_id,
               ad.day,
               row_number() over (partition by ad.marketplace_product_id order by random()) as rn
        from all_days ad
    ) ranked
    where rn <= 5
)
insert into price_history (marketplace, product_id, marketplace_product_id, price, recorded_at)
select ad.marketplace,
       ad.product_id,
       ad.marketplace_product_id,
       round((
           ad.base_price * (
               case
                   when psd.day is null then
                       (0.70 + random() * 0.60)
                   when ad.trend_dir = 1 then
                       case
                           when random() < 0.80 then (1.00 + random() * 0.50)
                           else (0.50 + random() * 0.50)
                       end
                   else
                       case
                           when random() < 0.80 then (0.50 + random() * 0.50)
                           else (1.00 + random() * 0.50)
                       end
               end
           )
       )::numeric, 2) as price,
       (ad.day + time '10:00')::timestamp as recorded_at
from all_days ad
left join pick_special_days psd
       on psd.marketplace_product_id = ad.marketplace_product_id
      and psd.day = ad.day;

commit;
