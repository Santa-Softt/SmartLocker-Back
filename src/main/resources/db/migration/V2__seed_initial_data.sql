INSERT INTO users (
    id,
    full_name,
    email,
    avatar_url,
    has_seen_welcome,
    suspended,
    suspension_time,
    role,
    version,
    receive_receipts,
    receives_promotions
)
VALUES (
    uuidv7(),
    'SysAdmin',
    '${admin_email}',
    'https://lh3.googleusercontent.com/a/ACg8ocKUBzUlUjQWJRQ7H5ipskQNIavhvFbUEHLT4fUIS7fuL5N9hzM=s288-c-no',
    true,
    false,
    NULL,
    'ADMIN',
    0,
    true,
    false
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO business_configs (
    id,
    hold_duration_seconds,
    min_rental_duration_minutes,
    max_rental_duration_minutes,
    penalty_percentage,
    streak_threshold,
    streak_discount_percentage,
    service_status
)
SELECT
    uuidv7(),
    ${business_hold_duration_seconds},
    ${business_min_rental_duration_minutes},
    ${business_max_rental_duration_minutes},
    ${business_penalty_percentage},
    ${business_streak_threshold},
    ${business_streak_discount_percentage},
    'OPERATIONAL'
WHERE NOT EXISTS (
    SELECT 1
    FROM business_configs
);

WITH active_config AS (
    SELECT id
    FROM business_configs
    LIMIT 1
),
seed_rates(size, hourly_rate) AS (
    VALUES
        ('XS', ${business_rate_xs}),
        ('S', ${business_rate_s}),
        ('M', ${business_rate_m}),
        ('L', ${business_rate_l}),
        ('XL', ${business_rate_xl})
)
INSERT INTO business_configs_rates (business_config_id, size, hourly_rate)
SELECT
    active_config.id,
    seed_rates.size,
    seed_rates.hourly_rate
FROM seed_rates
CROSS JOIN active_config
WHERE NOT EXISTS (
    SELECT 1
    FROM business_configs_rates existing_rate
    WHERE existing_rate.business_config_id = active_config.id
      AND existing_rate.size = seed_rates.size
);

WITH size_counts(size_order, size, locker_count) AS (
    VALUES
        (1, 'XS', ${locker_quantity_xs}),
        (2, 'S', ${locker_quantity_s}),
        (3, 'M', ${locker_quantity_m}),
        (4, 'L', ${locker_quantity_l}),
        (5, 'XL', ${locker_quantity_xl})
),
generated_lockers AS (
    SELECT
        size_counts.size_order,
        size_counts.size,
        series.size_index
    FROM size_counts
    CROSS JOIN LATERAL generate_series(1, size_counts.locker_count) AS series(size_index)
),
numbered_lockers AS (
    SELECT
        row_number() OVER (ORDER BY size_order, size_index) AS global_index,
        size,
        size_index
    FROM generated_lockers
)
INSERT INTO lockers (id, label, size, state)
SELECT
    uuidv7(),
    size || '-' || CASE
        WHEN size_index < 100 THEN lpad(size_index::text, 2, '0')
        ELSE size_index::text
    END,
    size,
    'AVAILABLE'
FROM numbered_lockers
ON CONFLICT (label) DO NOTHING;
