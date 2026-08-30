package com.routeplan.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class AuthMaintenance {
    private final AuthMailQueue queue;
    private final JdbcTemplate jdbc;

    public AuthMaintenance(AuthMailQueue queue, JdbcTemplate jdbc) {
        this.queue = queue;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${routeplan.auth.mail-poll-ms:5000}", initialDelayString = "${routeplan.auth.mail-poll-ms:5000}")
    public void deliver() {
        for (int i = 0; i < 10 && queue.processOne(); i++) { /* Bounded batch; database locks allow multiple nodes. */ }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanup() {
        jdbc.update("DELETE FROM auth_tokens WHERE expires_at <= now()");
        jdbc.update("DELETE FROM auth_rate_limits WHERE resets_at <= now()");
        jdbc.update("DELETE FROM auth_mail_jobs WHERE created_at < now() - interval '1 day'");
    }
}
