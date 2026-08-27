ALTER TABLE trips
    ADD COLUMN budget_currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    ADD COLUMN budget_limit_minor BIGINT,
    ADD COLUMN fixed_cost_minor BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_trips_budget_currency CHECK (budget_currency IN ('KRW','JPY','USD','EUR','GBP','CNY')),
    ADD CONSTRAINT ck_trips_budget_limit CHECK (budget_limit_minor BETWEEN 0 AND 1000000000000),
    ADD CONSTRAINT ck_trips_fixed_cost CHECK (fixed_cost_minor BETWEEN 0 AND 1000000000000);

ALTER TABLE trip_places
    ADD COLUMN estimated_cost_minor BIGINT,
    ADD CONSTRAINT ck_trip_places_cost CHECK (estimated_cost_minor BETWEEN 0 AND 1000000000000);

ALTER TABLE itineraries
    ADD COLUMN budget_currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    ADD COLUMN budget_limit_minor BIGINT,
    ADD COLUMN fixed_cost_minor BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_itineraries_budget_currency CHECK (budget_currency IN ('KRW','JPY','USD','EUR','GBP','CNY')),
    ADD CONSTRAINT ck_itineraries_budget_limit CHECK (budget_limit_minor BETWEEN 0 AND 1000000000000),
    ADD CONSTRAINT ck_itineraries_fixed_cost CHECK (fixed_cost_minor BETWEEN 0 AND 1000000000000);

ALTER TABLE itinerary_items
    ADD COLUMN estimated_cost_minor BIGINT,
    ADD CONSTRAINT ck_itinerary_items_cost CHECK (estimated_cost_minor BETWEEN 0 AND 1000000000000);
