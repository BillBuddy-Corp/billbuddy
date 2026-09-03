CREATE TABLE "files" (
  "id" bigserial PRIMARY KEY,
  "uploaded_by" bigint,
  "content_type" varchar NOT NULL,
  "file_size_bytes" bigint NOT NULL,
  "storage_key" varchar UNIQUE NOT NULL,
  "created_at" timestamp DEFAULT (now()),
  "deleted_at" timestamp
);

ALTER TABLE "files" ADD FOREIGN KEY ("uploaded_by") REFERENCES "users" ("id");
