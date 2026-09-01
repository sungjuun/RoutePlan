CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE places
    ADD COLUMN location GEOGRAPHY(POINT, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
        ) STORED;

CREATE INDEX idx_places_location_gist ON places USING GIST (location);

CREATE TABLE route_leg_cache (
    id BIGSERIAL PRIMARY KEY,
    cache_version SMALLINT NOT NULL DEFAULT 1,
    provider VARCHAR(40) NOT NULL,
    transport_mode VARCHAR(24) NOT NULL,
    origin_latitude_e6 INTEGER NOT NULL,
    origin_longitude_e6 INTEGER NOT NULL,
    destination_latitude_e6 INTEGER NOT NULL,
    destination_longitude_e6 INTEGER NOT NULL,
    departure_bucket TIMESTAMPTZ NOT NULL,
    origin GEOGRAPHY(POINT, 4326) NOT NULL,
    destination GEOGRAPHY(POINT, 4326) NOT NULL,
    distance_meters BIGINT NOT NULL,
    travel_minutes INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_route_leg_cache_lookup UNIQUE (
        cache_version,
        provider,
        transport_mode,
        origin_latitude_e6,
        origin_longitude_e6,
        destination_latitude_e6,
        destination_longitude_e6,
        departure_bucket
    ),
    CONSTRAINT ck_route_leg_cache_transport_mode
        CHECK (transport_mode IN ('WALKING', 'DRIVING', 'PUBLIC_TRANSIT')),
    CONSTRAINT ck_route_leg_cache_origin_latitude
        CHECK (origin_latitude_e6 BETWEEN -90000000 AND 90000000),
    CONSTRAINT ck_route_leg_cache_origin_longitude
        CHECK (origin_longitude_e6 BETWEEN -180000000 AND 180000000),
    CONSTRAINT ck_route_leg_cache_destination_latitude
        CHECK (destination_latitude_e6 BETWEEN -90000000 AND 90000000),
    CONSTRAINT ck_route_leg_cache_destination_longitude
        CHECK (destination_longitude_e6 BETWEEN -180000000 AND 180000000),
    CONSTRAINT ck_route_leg_cache_distance CHECK (distance_meters >= 0),
    CONSTRAINT ck_route_leg_cache_duration CHECK (travel_minutes >= 0)
);

CREATE INDEX idx_route_leg_cache_origin_gist ON route_leg_cache USING GIST (origin);
CREATE INDEX idx_route_leg_cache_destination_gist ON route_leg_cache USING GIST (destination);
CREATE INDEX idx_route_leg_cache_expiry ON route_leg_cache (expires_at, id);

CREATE TABLE route_cache_refresh_locks (
    lock_key CHAR(64) PRIMARY KEY,
    owner_token UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_route_cache_refresh_locks_expiry
    ON route_cache_refresh_locks (expires_at);
