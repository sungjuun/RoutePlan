ALTER TABLE trip_expenses
    ADD COLUMN place_id BIGINT REFERENCES places(id) ON DELETE SET NULL;

CREATE INDEX ix_trip_expenses_place ON trip_expenses(trip_id, place_id)
    WHERE place_id IS NOT NULL;
