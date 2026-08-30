# Groups API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

Balances (`GET /groups/{id}/balances[/simplified]`) are not implemented yet — deferred until Expenses/Settlements exist.

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
`description` optional. `defaultCurrency` must be a real ISO 4217 code (e.g. `INR`, `USD`, `EUR`) — validated against `java.util.Currency`, case-insensitive on input, always stored/returned uppercase. An unrecognized code returns `400 INVALID_CURRENCY`.

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

---

### `PUT /groups/{groupId}` — Admin only
Full update of name/description/currency.

**Request** — same shape as create (all three fields required).

**Response** `200 OK` — same shape as the create response.

---

### `DELETE /groups/{groupId}` — Admin only
Soft-deletes the group and revokes all its active invites.

**Response** `204 No Content`

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

---

### `DELETE /groups/{groupId}/members/{userId}`
Removes a member. An Admin can remove anyone; anyone can remove themselves (leave). The sole remaining Admin cannot leave while other active members exist (`409`).

**Response** `204 No Content`

---

### `PUT /groups/{groupId}/members/{userId}/role` — Admin only
Promotes or demotes a member. Demoting the sole remaining Admin while other members exist is blocked (`409`).

**Request**
```json
{ "role": "ADMIN" }
```
`role` is `"ADMIN"` or `"MEMBER"`.

**Response** `200 OK` — same shape as a member in the members list.

---

### `POST /groups/{groupId}/invites/email` — Admin only
Emails an invite link to the given address via Gmail SMTP. Token is stored hashed and never returned in any response.

**Request**
```json
{ "email": "friend@example.com" }
```

**Response** `201 Created`
```json
{ "message": "Invite sent" }
```

---

### `GET /groups/{groupId}/invites` — Admin only
Lists all pending invites, both email and shareable-link. Email-invite `token` is always `null` (delivered only via email); link-invite `token` is the raw, shareable value.

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

---

### `DELETE /groups/{groupId}/invites/{inviteId}` — Admin only
Revokes a specific pending invite (either type).

**Response** `204 No Content`

---

### `POST /groups/{groupId}/invites/link/generate` — Admin only
Creates a new shareable join link, invalidating any prior one for the group. Never expires.

**Response** `201 Created` — a `GroupInviteResponse` with `type: "LINK"` and `token` populated (see the list example above).

---

### `DELETE /groups/{groupId}/invites/link` — Admin only
Disables the group's active shareable link, if any. Safe to call even if there isn't one.

**Response** `204 No Content`

---

### `POST /invites/join`
Joins a group using either an email-invite or shareable-link token — same endpoint handles both.

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

Fails with `400` if the invite is revoked, expired, or already used; `404` if the token or its group doesn't exist; `409` if already a member.
