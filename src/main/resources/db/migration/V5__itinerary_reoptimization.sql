ALTER TABLE itineraries
    ADD COLUMN generation_type VARCHAR(30) NOT NULL DEFAULT 'INITIAL_OPTIMIZATION',
    ADD COLUMN parent_itinerary_id BIGINT,
    ADD COLUMN change_reason VARCHAR(30),
    ADD COLUMN change_reason_detail VARCHAR(500),
    ADD COLUMN reoptimization_start_time TIME,
    ADD COLUMN reoptimization_start_latitude NUMERIC(9, 6),
    ADD COLUMN reoptimization_start_longitude NUMERIC(10, 6),
    ADD CONSTRAINT fk_itineraries_parent
        FOREIGN KEY (parent_itinerary_id) REFERENCES itineraries (id),
    ADD CONSTRAINT ck_itineraries_reoptimization_metadata CHECK (
        (
            generation_type = 'INITIAL_OPTIMIZATION'
            AND parent_itinerary_id IS NULL
            AND change_reason IS NULL
            AND change_reason_detail IS NULL
            AND reoptimization_start_time IS NULL
            AND reoptimization_start_latitude IS NULL
            AND reoptimization_start_longitude IS NULL
        )
        OR
        (
            generation_type = 'REOPTIMIZATION'
            AND parent_itinerary_id IS NOT NULL
            AND change_reason IS NOT NULL
            AND reoptimization_start_time IS NOT NULL
            AND reoptimization_start_latitude BETWEEN -90 AND 90
            AND reoptimization_start_longitude BETWEEN -180 AND 180
        )
    );

CREATE INDEX idx_itineraries_parent_id ON itineraries (parent_itinerary_id);

ALTER TABLE itinerary_items
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    ADD CONSTRAINT ck_itinerary_items_status
        CHECK (status IN ('PLANNED', 'COMPLETED'));
