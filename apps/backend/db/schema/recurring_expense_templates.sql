CREATE TABLE "recurring_expense_templates" (
  "id" bigserial PRIMARY KEY,
  "group_id" bigint,
  "created_by" bigint,
  "description" varchar NOT NULL,
  "amount" decimal(10,2) NOT NULL,
  "currency" varchar NOT NULL DEFAULT 'INR',
  "exchange_rate" decimal(10,6),
  "category" varchar,
  "split_type" varchar NOT NULL,
  "frequency" varchar NOT NULL,
  "day_of_week" int,
  "day_of_month" int,
  "split_config" jsonb NOT NULL,
  "next_run_at" date NOT NULL,
  "active" boolean NOT NULL DEFAULT true,
  "created_at" timestamp DEFAULT (now()),
  "updated_at" timestamp DEFAULT (now()),
  "deleted_at" timestamp
);

ALTER TABLE "recurring_expense_templates" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "recurring_expense_templates" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id");
