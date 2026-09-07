CREATE TABLE "expense_comments" (
  "id" bigserial PRIMARY KEY,
  "expense_id" bigint,
  "user_id" bigint,
  "body" varchar NOT NULL,
  "created_at" timestamp DEFAULT (now())
);

ALTER TABLE "expense_comments" ADD FOREIGN KEY ("expense_id") REFERENCES "expenses" ("id");
ALTER TABLE "expense_comments" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
