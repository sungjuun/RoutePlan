ALTER TABLE itineraries
    ADD COLUMN reoptimization_start_date DATE;

UPDATE itineraries itinerary
SET reoptimization_start_date = trip.start_date
FROM trips trip
WHERE itinerary.trip_id = trip.id
  AND itinerary.generation_type = 'REOPTIMIZATION';

ALTER TABLE itineraries
    DROP CONSTRAINT ck_itineraries_reoptimization_metadata,
    ADD CONSTRAINT ck_itineraries_reoptimization_metadata CHECK (
        (
            generation_type = 'INITIAL_OPTIMIZATION'
            AND parent_itinerary_id IS NULL
            AND change_reason IS NULL
            AND change_reason_detail IS NULL
            AND reoptimization_start_date IS NULL
            AND reoptimization_start_time IS NULL
            AND reoptimization_start_latitude IS NULL
            AND reoptimization_start_longitude IS NULL
        )
        OR
        (
            generation_type = 'REOPTIMIZATION'
            AND parent_itinerary_id IS NOT NULL
            AND change_reason IS NOT NULL
            AND reoptimization_start_date IS NOT NULL
            AND reoptimization_start_time IS NOT NULL
            AND reoptimization_start_latitude BETWEEN -90 AND 90
            AND reoptimization_start_longitude BETWEEN -180 AND 180
        )
    );
