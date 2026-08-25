ALTER TABLE itineraries
    ADD COLUMN route_data_type VARCHAR(40) NOT NULL DEFAULT 'STRAIGHT_LINE_ESTIMATE',
    ADD COLUMN route_provider_call_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN route_matrix_element_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN route_matrix_build_millis BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_itineraries_route_provider_calls CHECK (route_provider_call_count >= 0),
    ADD CONSTRAINT ck_itineraries_route_matrix_elements CHECK (route_matrix_element_count >= 0),
    ADD CONSTRAINT ck_itineraries_route_matrix_build_millis CHECK (route_matrix_build_millis >= 0);
