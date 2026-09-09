# Friends API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

A friendship is a direct connection between two users, independent of any shared group. Adding a friend is instant and mutual, there is no accept/reject step, matching how group invites already work in this app. The other person must already have a BillBuddy account; inviting a non-user by email is not supported.

---

### `POST /friends`
Adds an existing user as a friend by email.

**Request**
```json
{ "email": "friend@example.com" }
```

**Response** `201 Created`
```json
{
  "userId": 2,
  "fullName": "Jane Doe",
  "email": "friend@example.com",
  "friendsSince": "2026-09-09T10:00:00"
}
```

Fails with `400 INVALID_FRIEND` if `email` belongs to the caller themselves, `404 FRIEND_NOT_FOUND` if no account exists with that email, or `409 ALREADY_FRIENDS` if the two are already friends.

---

### `GET /friends`
Lists everyone the caller has added as a friend.

**Response** `200 OK` — array of the same shape as the create response.

---

### `DELETE /friends/{friendUserId}`
Removes a friend connection. Either side can remove it.

**Response** `204 No Content`

Fails with `404 FRIENDSHIP_NOT_FOUND` if the caller and `friendUserId` aren't currently friends.

---

## Errors specific to Friends

| `error` | Status | Cause |
|---|---|---|
| `FRIEND_NOT_FOUND` | 404 | no account exists with the given email |
| `INVALID_FRIEND` | 400 | attempting to add yourself as a friend |
| `ALREADY_FRIENDS` | 409 | the two users are already friends |
| `FRIENDSHIP_NOT_FOUND` | 404 | the caller and the target user aren't currently friends |
