-- one cached extraction result per file. Only successful scans are persisted;
-- a failed scan (not a readable receipt) is never stored and can simply be retried.
CREATE TABLE "receipt_scans" (
  "id" bigserial PRIMARY KEY,
  "file_id" bigint UNIQUE,
  "merchant" varchar,
  "amount" decimal(10,2),
  "currency" varchar,
  "transaction_date" date,
  "other_discount" decimal(10,2),
  "voucher_amount" decimal(10,2),
  "subtotal" decimal(10,2),
  "needs_review" boolean NOT NULL DEFAULT false,
  "discounts_need_review" boolean NOT NULL DEFAULT false,
  "created_at" timestamp DEFAULT (now())
);

ALTER TABLE "receipt_scans" ADD FOREIGN KEY ("file_id") REFERENCES "files" ("id");
