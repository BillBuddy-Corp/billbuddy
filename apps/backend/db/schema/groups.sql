CREATE TABLE "groups" (
  "id" bigserial PRIMARY KEY,
  "name" varchar NOT NULL,
  "description" varchar,
  "default_currency" varchar NOT NULL DEFAULT 'INR',
  "created_by" bigint,
  "created_at" timestamp DEFAULT (now()),
  "updated_at" timestamp DEFAULT (now()),
  "deleted_at" timestamp
);

ALTER TABLE "groups" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id");
