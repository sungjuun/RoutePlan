ALTER TABLE trips ADD COLUMN time_zone_id VARCHAR(100) NOT NULL DEFAULT 'Asia/Seoul';
ALTER TABLE itineraries ADD COLUMN time_zone_id VARCHAR(100) NOT NULL DEFAULT 'Asia/Seoul';
ALTER TABLE itineraries ADD COLUMN data_warnings TEXT NOT NULL DEFAULT '';
ALTER TABLE itineraries ADD COLUMN travel_mode_snapshot VARCHAR(30);
ALTER TABLE itineraries ADD COLUMN hotel_latitude_snapshot NUMERIC(9,6);
ALTER TABLE itineraries ADD COLUMN hotel_longitude_snapshot NUMERIC(10,6);
ALTER TABLE trip_weather_forecasts ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
CREATE TABLE external_api_usage (
    operation VARCHAR(50) NOT NULL,
    usage_month DATE NOT NULL,
    units BIGINT NOT NULL CHECK (units >= 0),
    PRIMARY KEY (operation, usage_month)
);
