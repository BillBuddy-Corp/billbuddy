CREATE TABLE "users" (
  "id" bigserial PRIMARY KEY,
  "full_name" varchar NOT NULL,
  "email" varchar UNIQUE NOT NULL,
  "pending_email" varchar,
  "mobile_number" varchar UNIQUE,
  "password_hash" varchar,
  "profile_pic_file_id" bigint,
  "fcm_token" varchar,
  "default_currency" varchar DEFAULT 'INR',
  "email_verified_at" timestamp,
  "mobile_verified_at" timestamp,
  "auth_provider" varchar DEFAULT 'EMAIL',
  "last_login_at" timestamp,
  "password_updated_at" timestamp,
  "created_at" timestamp DEFAULT (now()),
  "updated_at" timestamp DEFAULT (now())
);
ALTER TABLE users
ADD CONSTRAINT users_auth_provider_check
CHECK (auth_provider IN ('EMAIL', 'GOOGLE', 'MOBILE'));
ALTER TABLE users
ALTER COLUMN created_at DROP DEFAULT,
ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE "users" ADD FOREIGN KEY ("profile_pic_file_id") REFERENCES "files" ("id");
