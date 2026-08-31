-- ITEMIZED split type only.
CREATE TABLE "expense_items" (
  "id" bigserial PRIMARY KEY,
  "expense_id" bigint,
  "name" varchar NOT NULL,
  "amount" decimal(10,2) NOT NULL
);

ALTER TABLE "expense_items" ADD FOREIGN KEY ("expense_id") REFERENCES "expenses" ("id");
