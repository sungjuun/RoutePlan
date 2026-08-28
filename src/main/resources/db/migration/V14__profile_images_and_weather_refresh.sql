CREATE TABLE user_avatars (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    image_data BYTEA NOT NULL CHECK (octet_length(image_data) <= 1048576),
    revision VARCHAR(36) NOT NULL
);

CREATE TABLE trip_weather_refresh (
    trip_id BIGINT PRIMARY KEY REFERENCES trips(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    next_refresh_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    last_error VARCHAR(300),
    lease_token VARCHAR(36)
);
CREATE INDEX idx_weather_refresh_due ON trip_weather_refresh(next_refresh_at) WHERE enabled;
