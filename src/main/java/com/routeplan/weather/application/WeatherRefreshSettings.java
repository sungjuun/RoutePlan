package com.routeplan.weather.application;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WeatherRefreshSettings {
    private final JdbcTemplate jdbc;
    private final AutomaticWeatherService weather;
    public WeatherRefreshSettings(JdbcTemplate jdbc, AutomaticWeatherService weather) { this.jdbc = jdbc; this.weather = weather; }

    public Settings get(long tripId) {
        return jdbc.query("SELECT enabled, next_refresh_at, last_success_at, last_error FROM trip_weather_refresh WHERE trip_id = ?",
                (rs, row) -> new Settings(rs.getBoolean(1), rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(), rs.getString(4)), tripId)
                .stream().findFirst().orElse(new Settings(false, null, null, null));
    }
    public Settings set(long tripId, boolean enabled) {
        // Repeated enables do not bypass the cadence. Disabling invalidates an in-flight lease.
        jdbc.update("""
                INSERT INTO trip_weather_refresh(trip_id, enabled) VALUES (?, ?)
                ON CONFLICT (trip_id) DO UPDATE SET enabled = EXCLUDED.enabled,
                  next_refresh_at = CASE WHEN EXCLUDED.enabled AND NOT trip_weather_refresh.enabled THEN now() ELSE trip_weather_refresh.next_refresh_at END,
                  lease_token = CASE WHEN EXCLUDED.enabled THEN trip_weather_refresh.lease_token ELSE NULL END
                """, tripId, enabled);
        return get(tripId);
    }

    /** Atomic leases prevent duplicate calls across workers. At most five due trips per tick. */
    public void refreshDue() {
        String token = UUID.randomUUID().toString();
        List<Long> ids = jdbc.query("""
                UPDATE trip_weather_refresh SET lease_token = ?, next_refresh_at = now() + interval '15 minutes'
                WHERE trip_id IN (
                  SELECT r.trip_id FROM trip_weather_refresh r JOIN trips t ON t.id = r.trip_id
                  WHERE r.enabled AND r.next_refresh_at <= now()
                    AND t.end_date >= CURRENT_DATE - 1 AND t.start_date <= CURRENT_DATE + 16
                  ORDER BY r.next_refresh_at, r.trip_id FOR UPDATE OF r SKIP LOCKED LIMIT 5
                ) RETURNING trip_id
                """, (rs, row) -> rs.getLong(1), token);
        for (long id : ids) {
            try {
                var refreshed = weather.refreshScheduled(id, () -> !jdbc.query("""
                        SELECT trip_id FROM trip_weather_refresh
                        WHERE trip_id = ? AND enabled AND lease_token = ? FOR UPDATE
                        """, (rs, row) -> rs.getLong(1), id, token).isEmpty());
                if (refreshed != null) jdbc.update("""
                        UPDATE trip_weather_refresh SET last_success_at = ?, last_error = NULL,
                            next_refresh_at = now() + interval '3 hours', lease_token = NULL
                        WHERE trip_id = ? AND lease_token = ? AND enabled
                        """, Timestamp.from(refreshed.fetchedAt()), id, token);
            } catch (RuntimeException exception) {
                // Keep the last good forecast and a safe message, not provider URLs or secrets.
                jdbc.update("""
                        UPDATE trip_weather_refresh SET last_error = ?, next_refresh_at = now() + interval '30 minutes', lease_token = NULL
                        WHERE trip_id = ? AND lease_token = ? AND enabled
                        """, "자동 갱신에 실패했습니다. 기존 예보를 유지하며 30분 후 다시 시도합니다.", id, token);
            }
        }
    }
    public record Settings(boolean enabled, Instant nextRefreshAt, Instant lastSuccessAt, String lastError) {}
}
