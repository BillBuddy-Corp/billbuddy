-- the "final answer" table regardless of split_type: every split type resolves down to
-- these (user_id, amount_owed) rows. percentage is only populated for PERCENTAGE-type expenses.
CREATE TABLE "expense_splits" (
  "id" bigserial PRIMARY KEY,
  "expense_id" bigint,
  "user_id" bigint,
  "amount_owed" decimal(10,2) NOT NULL,
  "percentage" decimal(5,2),
  UNIQUE ("expense_id", "user_id")
);

ALTER TABLE "expense_splits" ADD FOREIGN KEY ("expense_id") REFERENCES "expenses" ("id");
ALTER TABLE "expense_splits" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
