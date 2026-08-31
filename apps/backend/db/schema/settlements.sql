CREATE TABLE "settlements" (
  "id" bigserial PRIMARY KEY,
  "group_id" bigint,
  "paid_by" bigint,
  "paid_to" bigint,
  "created_by" bigint,
  "amount" decimal(10,2) NOT NULL,
  "currency" varchar NOT NULL DEFAULT 'INR',
  "note" varchar,
  "created_at" timestamp DEFAULT (now()),
  "updated_at" timestamp DEFAULT (now()),
  "deleted_at" timestamp
);

ALTER TABLE "settlements" ADD FOREIGN KEY ("group_id") REFERENCES "groups" ("id");
ALTER TABLE "settlements" ADD FOREIGN KEY ("paid_by") REFERENCES "users" ("id");
ALTER TABLE "settlements" ADD FOREIGN KEY ("paid_to") REFERENCES "users" ("id");
ALTER TABLE "settlements" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id");
