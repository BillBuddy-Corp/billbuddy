-- deliberately separate from auth_tokens: a 6-digit code needs an attempt counter a long random
-- token has no use for, and a much shorter expiry than the hours-long email/password tokens.
-- code_hash is always a SHA-256 hash; the raw code is only ever texted, never stored.
CREATE TABLE "mobile_otps" (
  "id" bigserial PRIMARY KEY,
  "user_id" bigint NOT NULL,
  "code_hash" varchar NOT NULL,
  "revoked" boolean NOT NULL DEFAULT false,
  "attempts_remaining" integer NOT NULL,
  "expires_at" timestamp NOT NULL,
  "used_at" timestamp,
  "created_at" timestamp DEFAULT (now())
);
ALTER TABLE "mobile_otps" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
