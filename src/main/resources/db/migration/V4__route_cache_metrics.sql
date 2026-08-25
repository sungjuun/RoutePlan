ALTER TABLE itineraries
    ADD COLUMN route_cache_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN route_cache_hit_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN route_cache_miss_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN route_cache_failure_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_itineraries_route_cache_hits CHECK (route_cache_hit_count >= 0),
    ADD CONSTRAINT ck_itineraries_route_cache_misses CHECK (route_cache_miss_count >= 0),
    ADD CONSTRAINT ck_itineraries_route_cache_failures CHECK (route_cache_failure_count >= 0),
    ADD CONSTRAINT ck_itineraries_route_cache_disabled_metrics CHECK (
        route_cache_enabled = TRUE
        OR (
            route_cache_hit_count = 0
            AND route_cache_miss_count = 0
            AND route_cache_failure_count = 0
        )
    );
