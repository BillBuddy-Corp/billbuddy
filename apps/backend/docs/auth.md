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
