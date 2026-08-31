CREATE TABLE "expenses" (
  "id" bigserial PRIMARY KEY,
  "group_id" bigint,
  "created_by" bigint,
  "description" varchar NOT NULL,
  "amount" decimal(10,2) NOT NULL,
  "currency" varchar NOT NULL DEFAULT 'INR',
  "converted_amount" decimal(10,2) NOT NULL,
  "exchange_rate" decimal(10,6) NOT NULL DEFAULT 1,
  "category" varchar,
  "receipt_file_id" bigint,
  "split_type" varchar NOT NULL,
  "created_at" timestamp DEFAULT (now()),
  "updated_at" timestamp DEFAULT (now()),
  "deleted_at" timestamp
);

ALTER TABLE "expenses" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "expenses" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id");
ALTER TABLE "expenses" ADD FOREIGN KEY ("receipt_file_id") REFERENCES "files" ("id");
