-- left_at is a soft-delete/rejoin marker, not an append-only join-history log:
-- each (group_id, user_id) pair has at most one row, reused across leave/rejoin cycles.
CREATE TABLE "group_members" (
  "id" bigserial PRIMARY KEY,
  "group_id" bigint,
  "user_id" bigint,
  "role" varchar NOT NULL DEFAULT 'MEMBER',
  "joined_at" timestamp DEFAULT (now()),
  "left_at" timestamp,
  UNIQUE ("group_id", "user_id")
);

ALTER TABLE "group_members" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "group_members" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
