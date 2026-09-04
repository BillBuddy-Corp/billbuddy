CREATE TABLE "receipt_scan_items" (
  "id" bigserial PRIMARY KEY,
  "receipt_scan_id" bigint,
  "name" varchar NOT NULL,
  "amount" decimal(10,2) NOT NULL,
  "quantity" integer NOT NULL DEFAULT 1
);

ALTER TABLE "receipt_scan_items" ADD FOREIGN KEY ("receipt_scan_id") REFERENCES "receipt_scans" ("id");
