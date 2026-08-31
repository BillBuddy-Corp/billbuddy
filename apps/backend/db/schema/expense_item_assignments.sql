-- ITEMIZED split type only. share is a plain number (default 1 for checkbox, any value for custom ratio).
CREATE TABLE "expense_item_assignments" (
  "id" bigserial PRIMARY KEY,
  "expense_item_id" bigint,
  "user_id" bigint,
  "share" decimal(10,2) NOT NULL DEFAULT 1,
  UNIQUE ("expense_item_id", "user_id")
);

ALTER TABLE "expense_item_assignments" ADD FOREIGN KEY ("expense_item_id") REFERENCES "expense_items" ("id");
ALTER TABLE "expense_item_assignments" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id");
