-- money paid IN, mirrors expense_splits structurally. sum(amount_paid) must equal expenses.amount.
CREATE TABLE "expense_payers" (
  "id" bigserial PRIMARY KEY,
  "expense_id" bigint,
  "user_id" bigint,
  "amount_paid" decimal(10,2) NOT NULL,
  UNIQUE ("expense_id", "user_id")
);

ALTER TABLE "expense_payers" ADD FOREIGN KEY ("expense_id") REFERENCES "expenses" ("id");
ALTER TABLE "expense_payers" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
