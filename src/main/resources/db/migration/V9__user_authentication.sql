ALTER TABLE users
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email),
    ADD CONSTRAINT ck_users_auth_pair CHECK (
        (email IS NULL AND password_hash IS NULL)
        OR (email IS NOT NULL AND password_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX idx_users_email_lower
    ON users (LOWER(email))
    WHERE email IS NOT NULL;
