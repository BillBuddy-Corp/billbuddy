# Notifications API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

## How it works

A notification is created automatically when something relevant happens, there's no endpoint to create one directly. Three things trigger one today:
- **A new expense is created** in a group — every other active group member gets notified (not the creator).
- **A comment is posted** on an expense — every other active group member gets notified (not the comment's author).
- **A settlement is recorded** — only the two parties involved (`paidBy`/`paidTo`) get notified, not the whole group, and not whoever recorded it if they're one of the two.

The message text is rendered once at write time (e.g. `"Jane Doe added an expense: Dinner (500.00 INR)"`) and stored as-is, it isn't reconstructed later from the referenced expense/settlement, so a notification stays readable even if the thing it refers to is later edited or deleted.

Delivery is in-app only for now — a notification is a row you read via `GET /notifications`, there's no real push yet. A `fcmToken` field can be registered via `PUT /users/me` (see `storage.md`) and a swappable push-sending interface exists behind the scenes, but the active implementation just logs instead of calling Firebase, the same placeholder pattern used for SMS in the mobile-verification flow.

---

### `GET /notifications`
Lists the authenticated user's notifications, newest first.

**Response** `200 OK`
```json
[
  {
    "id": 1,
    "type": "EXPENSE_CREATED",
    "message": "Jane Doe added an expense: Dinner (500.00 INR)",
    "groupId": 10,
    "expenseId": 100,
    "settlementId": null,
    "read": false,
    "createdAt": "2026-09-07T09:00:00"
  }
]
```
`type` is one of `EXPENSE_CREATED`, `COMMENT_POSTED`, `SETTLEMENT_RECORDED`. `expenseId`/`settlementId` are populated depending on `type`, only one is ever non-null, and `groupId` is always present regardless of type.

---

### `GET /notifications/unread-count`
Returns how many of the authenticated user's notifications are unread.

**Response** `200 OK`
```json
{ "unreadCount": 3 }
```

---

### `POST /notifications/{notificationId}/read`
Marks one notification as read. Only affects notifications belonging to the authenticated user, a `notificationId` that exists but belongs to someone else returns the same `404` as one that doesn't exist at all, to avoid confirming other users' notification ids.

**Response** `200 OK`

Fails with `404 NOTIFICATION_NOT_FOUND`.

---

### `POST /notifications/read-all`
Marks every currently-unread notification belonging to the authenticated user as read. No-ops if there are none.

**Response** `200 OK`

---

## Errors specific to Notifications

| `error` | Status | Cause |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | notification id doesn't exist, or doesn't belong to the caller |
