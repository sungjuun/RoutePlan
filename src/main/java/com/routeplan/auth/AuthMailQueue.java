package com.routeplan.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthMailQueue {
    private static final Logger log = LoggerFactory.getLogger(AuthMailQueue.class);
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final AuthMailSettings settings;
    private final TransactionTemplate transactions;

    public AuthMailQueue(JdbcTemplate jdbc, JavaMailSender mail, AuthMailSettings settings,
                         PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void enqueue(String email, String purpose) {
        if (!settings.enabled()) return;
        // An identical, fast database path for unknown and known email addresses; SMTP runs asynchronously.
        jdbc.update("""
                INSERT INTO auth_mail_jobs(user_id, purpose, security_version)
                SELECT id, ?, security_version FROM users WHERE email = ? AND password_hash IS NOT NULL
                  AND (? <> 'VERIFY_EMAIL' OR email_verified_at IS NULL)
                """, purpose, email, purpose);
    }

    public boolean processOne() {
        if (!settings.enabled()) return false;
        return Boolean.TRUE.equals(transactions.execute(status -> {
            List<Job> jobs = jdbc.query("""
                    SELECT id, user_id, purpose, security_version FROM auth_mail_jobs
                    WHERE available_at <= now() AND attempts < 5
                      AND created_at > now() - interval '1 day'
                    ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs, row) -> new Job(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4)));
            if (jobs.isEmpty()) return false;
            Job job = jobs.getFirst();
            List<Recipient> users = jdbc.query("""
                    SELECT email, security_version, email_verified_at IS NOT NULL FROM users
                    WHERE id = ? FOR UPDATE
                    """, (rs, row) -> new Recipient(rs.getString(1), rs.getLong(2), rs.getBoolean(3)), job.userId());
            if (users.isEmpty() || users.getFirst().version() != job.version()
                    || (job.purpose().equals("VERIFY_EMAIL") && users.getFirst().verified())) {
                jdbc.update("DELETE FROM auth_mail_jobs WHERE id = ?", job.id());
                return true;
            }
            Recipient recipient = users.getFirst();
            boolean notification = job.purpose().equals("PASSWORD_CHANGED");
            String token = notification ? null : AuthTokens.create();
            if (token != null) {
                Instant expires = Instant.now().plus(job.purpose().equals("VERIFY_EMAIL") ? 1440 : 30, ChronoUnit.MINUTES);
                jdbc.update("""
                        INSERT INTO auth_tokens(token_hash, user_id, purpose, security_version, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """, AuthTokens.hash(token), job.userId(), job.purpose(), job.version(), Timestamp.from(expires));
            }
            try {
                mail.send(message(recipient.email(), job.purpose(), token));
                jdbc.update("DELETE FROM auth_mail_jobs WHERE id = ?", job.id());
            } catch (MailException exception) {
                if (token != null) jdbc.update("DELETE FROM auth_tokens WHERE token_hash = ?", AuthTokens.hash(token));
                jdbc.update("""
                        UPDATE auth_mail_jobs SET attempts = attempts + 1,
                            available_at = now() + (power(2, attempts) * interval '1 minute') WHERE id = ?
                        """, job.id());
                // SMTP exceptions can contain addresses or message content; never log them or the link.
                log.warn("Account mail delivery failed; job={}, automatic retries are limited to five attempts", job.id());
            }
            return true;
        }));
    }

    private SimpleMailMessage message(String email, String purpose, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(email);
        if (purpose.equals("PASSWORD_CHANGED")) {
            message.setSubject("[RoutePlan] 비밀번호가 변경되었습니다");
            message.setText("계정 비밀번호가 변경되어 모든 기기에서 로그아웃했습니다.\n"
                    + "본인이 변경하지 않았다면 즉시 로그인 화면에서 비밀번호 재설정을 요청하세요.\n"
                    + settings.publicUrl() + "/");
        } else {
            boolean verify = purpose.equals("VERIFY_EMAIL");
            message.setSubject(verify ? "[RoutePlan] 이메일 주소를 인증해 주세요" : "[RoutePlan] 비밀번호 재설정");
            String link = settings.publicUrl() + "/#" + (verify ? "verify-email=" : "reset-password=") + token;
            message.setText((verify ? "이메일 인증" : "비밀번호 재설정") + "을 요청하셨습니다.\n"
                    + "아래 링크를 열고 화면에서 직접 완료해 주세요. 링크는 " + (verify ? "24시간" : "30분")
                    + " 동안 한 번만 사용할 수 있습니다.\n\n" + link
                    + "\n\n요청하지 않았다면 이 메일을 무시하세요. 링크를 다른 사람에게 전달하지 마세요.");
        }
        return message;
    }

    private record Job(long id, long userId, String purpose, long version) { }
    private record Recipient(String email, long version, boolean verified) { }
}
