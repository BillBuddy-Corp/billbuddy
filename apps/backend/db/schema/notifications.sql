CREATE TABLE "notifications" (
  "id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "type" varchar NOT NULL,
  "message" varchar NOT NULL,
  "group_id" bigint,
  "expense_id" bigint,
  "settlement_id" bigint,
  "read_at" timestamp,
  "created_at" timestamp DEFAULT (now())
);

ALTER TABLE "notifications" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
ALTER TABLE "notifications" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "notifications" ADD FOREIGN KEY ("expense_id") REFERENCES "expenses" ("id");
ALTER TABLE "notifications" ADD FOREIGN KEY ("settlement_id") REFERENCES "settlements" ("id");
