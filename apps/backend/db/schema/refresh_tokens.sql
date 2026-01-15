CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
ALTER TABLE refresh_tokens
  ADD COLUMN device_id VARCHAR NOT NULL DEFAULT 'unknown',
  ADD COLUMN device_name VARCHAR,
  ADD COLUMN ip_address VARCHAR,
  ADD COLUMN user_agent TEXT,
  ADD COLUMN last_used_at TIMESTAMP;

