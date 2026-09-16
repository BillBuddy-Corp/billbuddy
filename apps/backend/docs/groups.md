# Groups API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

Balances (`GET /groups/{id}/balances[/simplified]`) are documented in `settlements.md`, alongside the Settlements endpoints they're computed from.

---

### `POST /groups`
Creates a group. The creator automatically becomes its first Admin.

**Request**
```json
{
  "name": "Goa Trip",
  "description": "Beach house squad",
  "defaultCurrency": "INR"
}
```
`description` optional. `defaultCurrency` must be a real ISO 4217 code (e.g. `INR`, `USD`, `EUR`) — validated against `java.util.Currency`, case-insensitive on input, always stored/returned uppercase.

Fails with `400 INVALID_CURRENCY` if `defaultCurrency` isn't a real code.

**Response** `201 Created`
```json
{
  "id": 1,
  "name": "Goa Trip",
  "description": "Beach house squad",
  "defaultCurrency": "INR",
  "createdByUserId": 1,
  "createdByName": "Jane Doe",
  "memberCount": 1,
  "currentUserRole": "ADMIN",
  "createdAt": "2026-08-29T10:00:00",
  "updatedAt": "2026-08-29T10:00:00"
}
```

---

### `GET /groups`
Lists every group the authenticated user is an active member of.

**Response** `200 OK` — array of the same shape as the create response.

---

### `GET /groups/{groupId}`
Group details. Caller must be an active member.

**Response** `200 OK` — same shape as the create response.

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, or `404 GROUP_NOT_FOUND` if the group doesn't exist.

---

### `PUT /groups/{groupId}` — Admin only
Full update of name/description/currency.

**Request** — same shape as create (all three fields required).

**Response** `200 OK` — same shape as the create response.

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if they're a member but not an Admin, `404 GROUP_NOT_FOUND` if the group doesn't exist, or `400 INVALID_CURRENCY` if `defaultCurrency` isn't a real code.

---

### `DELETE /groups/{groupId}` — Admin only
Soft-deletes the group and revokes all its active invites. Blocked while any member still has a non-zero net balance (`409`), settle up via Settlements first (see `settlements.md`).

**Response** `204 No Content`

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if they're a member but not an Admin, `404 GROUP_NOT_FOUND` if the group doesn't exist, or `409 UNSETTLED_BALANCES` if any member still has a non-zero net balance.

---

### `GET /groups/{groupId}/members`
Lists active members. Caller must be a member.

**Response** `200 OK`
```json
[
  {
    "userId": 1,
    "fullName": "Jane Doe",
    "email": "jane@example.com",
    "role": "ADMIN",
    "joinedAt": "2026-08-29T10:00:00"
  }
]
```

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, or `404 GROUP_NOT_FOUND` if the group doesn't exist.

---

### `DELETE /groups/{groupId}/members/{userId}`
Removes a member. An Admin can remove anyone; anyone can remove themselves (leave). The sole remaining Admin cannot leave while other active members exist (`409`).

**Response** `204 No Content`

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if removing someone other than yourself and you're not an Admin, `404 GROUP_NOT_FOUND` if the group doesn't exist, `404 MEMBER_NOT_FOUND` if `userId` isn't an active member, or `409 LAST_ADMIN` if you're the sole remaining Admin leaving while other members exist.

---

### `PUT /groups/{groupId}/members/{userId}/role` — Admin only
Promotes or demotes a member. Demoting the sole remaining Admin while other members exist is blocked (`409`).

**Request**
```json
{ "role": "ADMIN" }
```
`role` is `"ADMIN"` or `"MEMBER"`.

**Response** `200 OK` — same shape as a member in the members list.

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if they're a member but not an Admin, `404 GROUP_NOT_FOUND` if the group doesn't exist, `404 MEMBER_NOT_FOUND` if `userId` isn't an active member, or `409 LAST_ADMIN` if demoting the sole remaining Admin while other members exist.

---

### `POST /groups/{groupId}/invites/email`
Emails an invite link to the given address via Gmail SMTP. Token is stored hashed and never returned in any response. Any active group member can send one, not just Admins.

**Request**
```json
{ "email": "friend@example.com" }
```

**Response** `201 Created`
```json
{ "message": "Invite sent" }
```

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `404 GROUP_NOT_FOUND` if the group doesn't exist, or `409 ALREADY_GROUP_MEMBER` if `email` already belongs to an active member.

---

### `GET /groups/{groupId}/invites`
Lists all pending invites, both email and shareable-link. Email-invite `token` is always `null` (delivered only via email); link-invite `token` is the raw, shareable value. Any active group member can view this list, not just Admins.

**Response** `200 OK`
```json
[
  {
    "id": 5,
    "type": "LINK",
    "email": null,
    "token": "AbC123...",
    "expiresAt": null,
    "acceptedAt": null,
    "revoked": false,
    "invitedByName": "Jane Doe",
    "createdAt": "2026-08-29T10:00:00"
  },
  {
    "id": 6,
    "type": "EMAIL",
    "email": "friend@example.com",
    "token": null,
    "expiresAt": "2026-09-05T10:00:00",
    "acceptedAt": null,
    "revoked": false,
    "invitedByName": "Jane Doe",
    "createdAt": "2026-08-29T10:05:00"
  }
]
```

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member.

---

### `DELETE /groups/{groupId}/invites/{inviteId}` — Admin only
Revokes a specific pending invite (either type).

**Response** `204 No Content`

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if they're a member but not an Admin, or `404 INVITE_NOT_FOUND` if `inviteId` doesn't exist or doesn't belong to this group.

---

### `POST /groups/{groupId}/invites/link/generate` — Admin only
Creates a new shareable join link, invalidating any prior one for the group. Never expires.

**Response** `201 Created` — a `GroupInviteResponse` with `type: "LINK"` and `token` populated (see the list example above). This is the raw token only, not a full link — the client builds the shareable link itself as a deep link (`billbuddy://join-group?token=...`), the same scheme an email invite's link uses under the hood.

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, `403 NOT_GROUP_ADMIN` if they're a member but not an Admin, or `404 GROUP_NOT_FOUND` if the group doesn't exist.

---

### `DELETE /groups/{groupId}/invites/link` — Admin only
Disables the group's active shareable link, if any. Safe to call even if there isn't one.

**Response** `204 No Content`

Fails with `403 NOT_GROUP_MEMBER` if the caller isn't an active member, or `403 NOT_GROUP_ADMIN` if they're a member but not an Admin.

---

### `POST /invites/join`
Joins a group using either an email-invite or shareable-link token — same endpoint handles both. An email invite's link is a deep link (`billbuddy://join-group?token=...`); tapping it opens the app directly to a screen that reads the token and calls this endpoint, same pattern as the reset-password and verify-email links (see `auth.md`).

**Request**
```json
{ "token": "AbC123..." }
```

**Response** `200 OK`
```json
{
  "groupId": 1,
  "groupName": "Goa Trip",
  "role": "MEMBER",
  "message": "Joined group successfully"
}
```

Fails with `400 INVALID_INVITE` if the invite is revoked, expired, or already used; `404 INVITE_NOT_FOUND` if the token doesn't match any invite, `404 GROUP_NOT_FOUND` if the invite's group has since been deleted, or `409 ALREADY_GROUP_MEMBER` if the caller is already an active member.

---

## Errors specific to Groups

| `error` | Status | Cause |
|---|---|---|
| `GROUP_NOT_FOUND` | 404 | group id doesn't exist or is soft-deleted |
| `NOT_GROUP_MEMBER` | 403 | caller isn't an active member of the group |
| `NOT_GROUP_ADMIN` | 403 | caller is an active member but not an Admin, on an Admin-only action |
| `MEMBER_NOT_FOUND` | 404 | target user id isn't an active member of the group |
| `LAST_ADMIN` | 409 | the sole remaining Admin tried to leave or step down while other active members exist |
| `INVITE_NOT_FOUND` | 404 | invite id/token doesn't match any invite, or doesn't belong to the given group |
| `INVALID_INVITE` | 400 | the invite has been revoked, expired, or (for email invites) already used |
| `ALREADY_GROUP_MEMBER` | 409 | the target email/user is already an active member of the group |
| `UNSETTLED_BALANCES` | 409 | a member still has a non-zero net balance, blocking group deletion |
