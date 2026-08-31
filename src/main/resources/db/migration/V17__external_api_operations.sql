ALTER TABLE external_api_usage
    ADD COLUMN attempt_count BIGINT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN success_count BIGINT NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    ADD COLUMN failure_count BIGINT NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    ADD COLUMN successful_units BIGINT NOT NULL DEFAULT 0 CHECK (successful_units >= 0),
    ADD COLUMN failed_units BIGINT NOT NULL DEFAULT 0 CHECK (failed_units >= 0),
    ADD COLUMN total_latency_ms BIGINT NOT NULL DEFAULT 0 CHECK (total_latency_ms >= 0),
    ADD COLUMN max_latency_ms BIGINT NOT NULL DEFAULT 0 CHECK (max_latency_ms >= 0),
    ADD COLUMN input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    ADD COLUMN output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_tokens >= 0);

ALTER TABLE external_api_usage
    ADD CONSTRAINT external_api_usage_completed_attempts_check
    CHECK (success_count + failure_count <= attempt_count),
    ADD CONSTRAINT external_api_usage_classified_units_check
    CHECK (successful_units + failed_units <= units);
