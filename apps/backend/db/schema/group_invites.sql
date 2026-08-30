-- token holds a SHA-256 hash for EMAIL-type rows (delivered via email, never re-shown),
-- and a raw token for LINK-type rows (no delivery channel other than the API response).
CREATE TABLE "group_invites" (
  "id" bigserial PRIMARY KEY,
  "group_id" bigint,
  "type" varchar NOT NULL,
  "email" varchar,
  "token" varchar UNIQUE NOT NULL,
  "invited_by" bigint,
  "revoked" boolean DEFAULT false,
  "expires_at" timestamp,
  "accepted_at" timestamp,
  "created_at" timestamp DEFAULT (now())
);

ALTER TABLE "group_invites" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "group_invites" ADD FOREIGN KEY ("invited_by") REFERENCES "users" ("id");
