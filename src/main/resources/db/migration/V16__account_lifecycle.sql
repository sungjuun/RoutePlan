-- Removing an account must remove data that identifies or is owned by that user.
-- Resolution history created by another reporter is retained, but the resolver is anonymized.
ALTER TABLE trips
    DROP CONSTRAINT fk_trips_user,
    ADD CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE shared_routes
    DROP CONSTRAINT fk_shared_routes_user,
    ADD CONSTRAINT fk_shared_routes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE route_likes
    DROP CONSTRAINT fk_route_likes_user,
    ADD CONSTRAINT fk_route_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE route_comments
    DROP CONSTRAINT route_comments_user_id_fkey,
    ADD CONSTRAINT route_comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE route_reviews
    DROP CONSTRAINT route_reviews_user_id_fkey,
    ADD CONSTRAINT route_reviews_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE community_reports
    DROP CONSTRAINT community_reports_reporter_id_fkey,
    ADD CONSTRAINT community_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    DROP CONSTRAINT community_reports_resolved_by_fkey,
    ADD CONSTRAINT community_reports_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL;
