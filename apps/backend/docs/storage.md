# Storage & User Profile API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

## How file storage works

Files are stored on local disk (a Docker-volume-mounted directory, same durability model as Postgres's own volume) behind a `FileStorageService` interface — swapping to S3/Cloudinary later is a new implementation class and a config change, not a rewrite of any endpoint. Every upload is validated as an image (`image/jpeg`, `image/png`, `image/webp`) up to 5MB.

**Upload then attach, in two steps** — there's no dedicated "upload profile picture" or "upload receipt" endpoint. Upload once via `POST /files`, then pass the returned `id` wherever it's needed:
```
POST /files  ->  { "id": 42, ... }
PUT /users/me { "profilePicFileId": 42, ... }
POST /groups/{groupId}/expenses { "receiptFileId": 42, ... }
```
You can only attach a file *you* uploaded — attaching someone else's file id fails with `403`.

**Who can view a file** is decided on read, not stored: `GET /files/{id}` figures out what the file is attached to and applies the matching rule — a receipt requires active membership in that expense's group; a profile picture is visible to any authenticated user; a file that isn't attached to anything yet is visible only to whoever uploaded it.

---

### `POST /files`
Uploads a file. Any authenticated user can upload.

**Request** — `multipart/form-data`, field name `file`.

**Response** `201 Created`
```json
{
  "id": 42,
  "url": "/api/v1/files/42",
  "contentType": "image/png",
  "fileSizeBytes": 84213,
  "createdAt": "2026-08-31T13:51:39"
}
```

Fails with `400 INVALID_FILE` if the file is missing, isn't `image/jpeg`/`image/png`/`image/webp`, or exceeds 5MB.

---

### `GET /files/{fileId}`
Returns the raw file bytes with the stored `Content-Type` header — not a JSON response.

**Response** `200 OK` — binary body.

Fails with `404 STORED_FILE_NOT_FOUND` if the id doesn't exist, `403 NOT_GROUP_MEMBER` if it's a receipt and you're not an active member of that expense's group, or `403 NOT_FILE_OWNER` if it isn't attached to anything yet and you're not the uploader.

---

### `GET /users/me`
Returns the authenticated user's profile.

**Response** `200 OK`
```json
{
  "id": 1,
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "pendingEmail": null,
  "mobileNumber": null,
  "mobileVerified": false,
  "profilePicUrl": "/api/v1/files/42",
  "defaultCurrency": "INR",
  "createdAt": "2026-08-29T10:00:00"
}
```
`profilePicUrl` is `null` if no picture has been set. `mobileVerified` is only ever `true` if `mobileNumber` is also set, see `auth.md` for the OTP flow that sets it. `pendingEmail` is set while an email change is awaiting confirmation (`email` itself doesn't change until then), see `auth.md` for the change-email flow.

---

### `PUT /users/me`
Updates the authenticated user's own profile. Deliberately limited to safe, non-verification-dependent fields.

**Request**
```json
{
  "fullName": "Jane Doe",
  "profilePicFileId": 42,
  "defaultCurrency": "INR",
  "mobileNumber": "+919876543210",
  "fcmToken": "device-push-token"
}
```
`profilePicFileId` is optional — omit or set to `null` to remove the picture. `defaultCurrency` is validated as a real ISO 4217 code the same way group currencies are. `mobileNumber` is optional; changing it (including setting one for the first time) resets `mobileVerified` to `false` and automatically sends a fresh OTP, see `auth.md` for the verify-mobile flow that confirms it. `fcmToken` is optional and write-only (never echoed back) — registers the calling device for push notifications; unlike the other fields, omitting it leaves whatever's currently registered unchanged rather than clearing it, since it's refreshed independently by the client's own push SDK, not something edited in a profile form. See `notifications.md` for what actually gets sent.

**Response** `200 OK` — same shape as `GET /users/me`.

Fails with `403 NOT_FILE_OWNER` if `profilePicFileId` references a file you didn't upload, `404 STORED_FILE_NOT_FOUND` if it doesn't exist, or `400 INVALID_CURRENCY` if `defaultCurrency` isn't a real code.

**Not covered by this endpoint, on purpose**: `email` and `password`. Email has its own confirm-before-change flow, `POST /auth/change-email` and `POST /auth/confirm-email-change` (see `auth.md`), since it's the account's login credential and needs re-verification before it actually takes effect, not a same-request field update. Password changes belong in the dedicated `/auth/change-password` flow, not a generic profile update.

---

## Errors specific to Storage

| `error` | Status | Cause |
|---|---|---|
| `STORED_FILE_NOT_FOUND` | 404 | file id doesn't exist or is soft-deleted |
| `INVALID_FILE` | 400 | missing file, unsupported content type, or over the 5MB limit |
| `NOT_FILE_OWNER` | 403 | attaching a file you didn't upload, or viewing an unattached file that isn't yours |
