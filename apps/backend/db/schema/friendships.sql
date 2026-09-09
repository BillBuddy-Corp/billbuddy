-- One row per friend pair, normalized so user_low_id is always the smaller id -- this keeps
-- adding a friend from either direction collapsing to a single unique row instead of two.
CREATE TABLE "friendships" (
  "id" bigserial PRIMARY KEY,
  "user_low_id" bigint NOT NULL,
  "user_high_id" bigint NOT NULL,
  "created_at" timestamp DEFAULT (now()),
  UNIQUE ("user_low_id", "user_high_id")
);

ALTER TABLE "friendships" ADD FOREIGN KEY ("user_low_id") REFERENCES "users" ("id");
ALTER TABLE "friendships" ADD FOREIGN KEY ("user_high_id") REFERENCES "users" ("id");
