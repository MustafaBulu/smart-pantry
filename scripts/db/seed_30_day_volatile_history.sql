-- Rebuild last 30 days of price_history for all mapped products.
-- Rules:
-- - 30 days per product (today included)
-- - today price = latest known price (anchor)
-- - max 10 non-today days use +/-50%
-- - remaining non-today days use +/-30%

begin;

with latest as (
    select distinct on (ph.marketplace_product_id)
           ph.marketplace_product_id,
           ph.product_id,
           ph.marketplace,
           ph.price::numeric(12, 2) as anchor_price
    from price_history ph
    where ph.price is not null
    order by ph.marketplace_product_id, ph.recorded_at desc
),
deleted as (
    delete from price_history ph
    using latest l
    where ph.marketplace_product_id = l.marketplace_product_id
      and ph.recorded_at >= (current_date - interval '29 day')
      and ph.recorded_at < (current_date + interval '1 day')
    returning ph.marketplace_product_id
),
day_series as (
    select generate_series(0, 29) as day_offset
),
base as (
    select
        l.marketplace_product_id,
        l.product_id,
        l.marketplace,
        l.anchor_price,
        ds.day_offset,
        (((hashtextextended(l.marketplace_product_id::text, 17) & 2147483647)::int % 11)) as extreme_limit,
        ((hashtextextended(l.marketplace_product_id::text || ':' || ds.day_offset::text || ':rank', 23) & 9223372036854775807)::numeric
            / 9223372036854775807::numeric) as rank_u,
        ((hashtextextended(l.marketplace_product_id::text || ':' || ds.day_offset::text || ':factor', 31) & 9223372036854775807)::numeric
            / 9223372036854775807::numeric) as factor_u
    from latest l
    cross join day_series ds
),
ranked as (
    select
        b.*,
        case
            when b.day_offset = 0 then null
            else row_number() over (
                partition by b.marketplace_product_id
                order by b.rank_u
            )
        end as rn_non_today
    from base b
),
prepared as (
    select
        r.marketplace,
        r.product_id,
        r.marketplace_product_id,
        (current_date - (r.day_offset || ' day')::interval + make_interval(hours => 10, mins => (r.marketplace_product_id % 50)::int))::timestamp as recorded_at,
        case
            when r.day_offset = 0 then r.anchor_price
            when r.rn_non_today <= r.extreme_limit
                then greatest(0.10, round((r.anchor_price * (0.50 + r.factor_u * 1.00))::numeric, 2))
            else greatest(0.10, round((r.anchor_price * (0.70 + r.factor_u * 0.60))::numeric, 2))
        end as price
    from ranked r
)
insert into price_history (marketplace, product_id, marketplace_product_id, price, recorded_at)
select marketplace, product_id, marketplace_product_id, price, recorded_at
from prepared;

commit;
