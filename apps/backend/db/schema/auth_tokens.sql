-- unifies password-reset and email-verification tokens in one table, same pattern
-- as group_invites unifying EMAIL/LINK invites via a type/purpose discriminator.
-- token_hash is always a SHA-256 hash; the raw token is only ever emailed, never stored.
CREATE TABLE "auth_tokens" (
  "id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "purpose" varchar NOT NULL,
  "token_hash" varchar UNIQUE NOT NULL,
  "revoked" boolean DEFAULT false,
  "expires_at" timestamp NOT NULL,
  "used_at" timestamp,
  "created_at" timestamp DEFAULT (now())
);
ALTER TABLE "auth_tokens" ADD CONSTRAINT auth_tokens_purpose_check
CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION', 'EMAIL_CHANGE'));
ALTER TABLE "auth_tokens" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
