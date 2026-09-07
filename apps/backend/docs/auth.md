# Authentication API

Base path: `/api/v1/auth`. Endpoints marked **Auth required** need `Authorization: Bearer <accessToken>`.

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

---

### `POST /auth/signup`
Register a new account.

**Request**
```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "mobileNumber": "+919876543210",
  "password": "password123"
}
```
`mobileNumber` optional. `password` min 8 characters.

**Response** `201 Created`
```json
{
  "id": 1,
  "email": "jane@example.com",
  "fullName": "Jane Doe",
  "createdAt": "2026-08-29T10:00:00"
}
```

---

### `POST /auth/login`
Authenticate and receive tokens. Logging in again on the same `deviceId` revokes the prior session on that device.

**Request**
```json
{
  "email": "jane@example.com",
  "password": "password123",
  "deviceId": "device-abc",
  "deviceName": "iPhone 15"
}
```
`deviceName` optional.

**Response** `200 OK`
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "userId": 1,
  "email": "jane@example.com",
  "fullName": "Jane Doe"
}
```

---

### `POST /auth/google`
Signs in with Google. The client performs "Sign in with Google" itself via Google's own SDK, then sends the resulting ID token here, there's no redirect/callback handled by this backend. Logs into an existing Google-linked account, or creates one on first sign-in, either way returning the same token shape as `POST /auth/login`.

**Request**
```json
{
  "idToken": "eyJ...",
  "deviceId": "device-abc",
  "deviceName": "iPhone 15"
}
```
`idToken` is the raw Google-signed JWT from the client's sign-in SDK, not one of our own tokens. `deviceName` optional, same as `login`.

**Response** `200 OK` — same shape as `POST /auth/login`.

Fails with `401 INVALID_CREDENTIALS` if the token is invalid/expired/wrong-audience, or if Google reports the account's email as unverified, or `409 USER_ALREADY_EXISTS` if the email is already registered on a password-based account (sign in with the password instead, accounts aren't auto-linked by matching email).

---

### `POST /auth/refreshtoken`
Exchange a refresh token for a new access + refresh pair. The old refresh token is revoked (rotation) — reusing it fails.

**Request**
```json
{
  "refreshToken": "eyJ...",
  "deviceId": "device-abc"
}
```

**Response** `200 OK` — same shape as `POST /auth/login`.

---

### `POST /auth/logout` — **Auth required**
Revokes the refresh token for the current device.

**Request**
```json
{
  "refreshToken": "eyJ...",
  "deviceId": "device-abc"
}
```

**Response** `200 OK`
```json
{ "message": "Logout successful" }
```

---

### `POST /auth/logout-all` — **Auth required**
Revokes every active session across all devices for the authenticated user. No request body.

**Response** `200 OK`
```json
{ "message": "Logged out from all devices" }
```

---

### `GET /auth/sessions` — **Auth required**
Lists all active sessions for the authenticated user. Requires an `X-Device-Id` header so the response can flag which session is the caller's own.

**Response** `200 OK`
```json
{
  "sessions": [
    {
      "id": 10,
      "deviceId": "device-abc",
      "deviceName": "iPhone 15",
      "ipAddress": "127.0.0.1",
      "userAgent": "Mozilla/5.0",
      "createdAt": "2026-08-29T10:00:00",
      "expiresAt": "2026-09-28T10:00:00",
      "current": true
    }
  ]
}
```

---

## Password reset, change, and email verification

Signup now also fires a verification email automatically, best-effort — a transient send failure never fails signup itself, and `POST /auth/verify-email/resend` is the fallback if it never arrives.

The verify/reset flow is link-based, matching how group invite links already work: the email contains a link to a frontend page carrying the raw token as a query parameter; the frontend reads it and calls the corresponding API below. The raw token is never persisted anywhere — only its SHA-256 hash is stored — so it only ever exists inside the email itself.

Password reset and password change both revoke every active session (all refresh tokens) for the account, forcing re-login on every device. Unverified accounts are **not** blocked from doing anything — verification status is tracked and exposed, nothing is gated on it.

---

### `POST /auth/forgot-password`
Requests a password reset. Always returns `200`, regardless of whether the email is registered — this is deliberate, to avoid revealing which addresses have accounts.

**Request**
```json
{ "email": "jane@example.com" }
```

**Response** `200 OK`
```json
{ "message": "If that email is registered, a reset link has been sent" }
```

---

### `POST /auth/reset-password`
Consumes a password reset token, sets a new password, and revokes every active session.

**Request**
```json
{
  "token": "AbC123...",
  "newPassword": "newpassword123"
}
```
`newPassword` min 8 characters.

**Response** `200 OK`
```json
{ "message": "Password reset successful" }
```

Fails with `400 INVALID_AUTH_TOKEN` if the token doesn't exist, is expired, was already used, or was superseded by a newer reset request (requesting a new reset invalidates any prior outstanding one).

---

### `POST /auth/change-password` — **Auth required**
Changes the authenticated user's password and revokes every active session (including, eventually, the one used to make this call — the access token stays valid until it naturally expires, but no refresh token will renew it).

**Request**
```json
{
  "currentPassword": "oldpassword123",
  "newPassword": "newpassword123"
}
```

**Response** `200 OK`
```json
{ "message": "Password changed successfully" }
```

Fails with `401 INVALID_CREDENTIALS` if `currentPassword` doesn't match.

---

### `POST /auth/verify-email`
Consumes an email verification token and marks the account's email as verified.

**Request**
```json
{ "token": "AbC123..." }
```

**Response** `200 OK`
```json
{ "message": "Email verified successfully" }
```

Fails with `400 INVALID_AUTH_TOKEN` under the same conditions as `reset-password`.

---

### `POST /auth/verify-email/resend` — **Auth required**
Resends the verification email. No-ops if the account is already verified — no new email is sent.

**Response** `200 OK`
```json
{ "message": "Verification email sent" }
```
or, if already verified:
```json
{ "message": "Email already verified" }
```

---

## Mobile number verification

A mobile number can be given at signup, or added or changed later via `PUT /users/me` (see `storage.md`). Either way, whenever the number changes, an OTP is sent automatically (same best-effort, never-fails-the-caller pattern as the verification email) and any prior verification status is reset, since a new number is by definition unverified.

The code is a 6-digit number, texted by an `SmsService` (swappable, same pattern as `FileStorageService` in `storage.md`). No real SMS provider is wired in yet, the active implementation just logs the code, so the flow is fully testable locally without any SMS account or cost, ready to swap in a real provider later. Unlike the email/password tokens, an OTP expires in minutes rather than hours, and only allows a limited number of wrong guesses before it's rejected outright, even with the correct code, since a 6-digit code is guessable within a short window in a way a long random token isn't.

---

### `POST /auth/verify-mobile` (**Auth required**)
Submits the code to verify the authenticated user's mobile number.

**Request**
```json
{ "code": "123456" }
```

**Response** `200 OK`
```json
{ "message": "Mobile number verified successfully" }
```

Fails with `400 INVALID_OTP` if the code is wrong, expired, already used, or the attempt limit has been used up (a fresh code via `verify-mobile/resend` is needed at that point, even to retry the originally-correct one).

---

### `POST /auth/verify-mobile/resend` (**Auth required**)
Sends a new code, invalidating any previous one. No-ops if the mobile number is already verified.

**Response** `200 OK`
```json
{ "message": "Verification code sent" }
```
or, if already verified:
```json
{ "message": "Mobile number already verified" }
```

Fails with `400 MOBILE_NUMBER_NOT_SET` if the account has no mobile number on file yet.

---

## Errors specific to Auth

| `error` | Status | Cause |
|---|---|---|
| `USER_ALREADY_EXISTS` | 409 | signup email already registered, or Google sign-in with an email already registered on a password-based account |
| `INVALID_CREDENTIALS` | 401 | wrong login password, invalid/expired/revoked refresh token, wrong `currentPassword` on change-password, or an invalid/unverified Google token on Google sign-in |
| `INVALID_AUTH_TOKEN` | 400 | a password reset or email verification token is missing, expired, revoked, or already used |
| `INVALID_OTP` | 400 | the mobile verification code is wrong, expired, already used, or attempts are exhausted |
| `MOBILE_NUMBER_NOT_SET` | 400 | requested a mobile verification code with no mobile number on the account |
