# Expenses API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

Balances/Settlements are computed from this data — see `settlements.md`.

---

## Split types

One create/update shape handles all four, discriminated by `splitType`. Only the field(s) matching the chosen type are required; the rest are ignored.

| `splitType` | Required field | How the split is computed |
|---|---|---|
| `EQUAL` | `participantUserIds` | `amount` divided evenly across participants |
| `PERCENTAGE` | `percentages` | each entry's `percentage` of `amount`; percentages must sum to exactly 100 |
| `EXACT` | `exactAmounts` | client-supplied amounts, must sum to `amount` |
| `ITEMIZED` | `items` | each item's `amount` divided across its `assignments` by `share`; per-user amounts are aggregated across all items into the final split |

**Rounding**: every computed share is floored to 2 decimals; any leftover cents from that division are handed out one at a time to participants sorted by user id, starting at an index that rotates per expense (`expense.id % participantCount`, or `item.id % participantCount` for `ITEMIZED`) — so the same person isn't systematically left with the rounding dust on every expense. `EXACT` and payer amounts need no remainder distribution since the client already supplies exact values.

`expense_splits` is always the final answer regardless of split type — `sum(splits.amountOwed) == convertedAmount`, including the payer's own share (that's intentional bookkeeping, not a self-debt; net balance = paid − owed, computed later by Balances).

**Payers**: `payers` is always required (at least one). `sum(payers.amountPaid)` must equal `amount`. Multiple payers per expense are supported.

**Currency**: `currency` must match the group's `defaultCurrency` exactly (normalized/validated the same way as group creation) — cross-currency expenses aren't supported yet. `exchangeRate` is always `1` and `convertedAmount == amount` for now; the schema has room for real FX conversion as a future feature.

---

### `POST /groups/{groupId}/expenses`
Creates an expense. Caller must be an active group member.

**Request** (EQUAL example)
```json
{
  "description": "Dinner",
  "amount": 100,
  "currency": "INR",
  "category": "Food",
  "receiptFileId": null,
  "splitType": "EQUAL",
  "payers": [
    { "userId": 1, "amountPaid": 100 }
  ],
  "participantUserIds": [1, 2]
}
```
`category` is a plain optional string. `receiptFileId` is optional — upload the receipt image via `POST /files` first (see `storage.md`), then pass the returned id here; you can only reference a file you uploaded yourself. For `PERCENTAGE`, replace `participantUserIds` with `percentages: [{ "userId": 1, "percentage": 60 }, ...]`. For `EXACT`, use `exactAmounts: [{ "userId": 1, "amount": 60 }, ...]`. For `ITEMIZED`, use `items: [{ "name": "Pizza", "amount": 60, "assignments": [{ "userId": 1, "share": 1 }, { "userId": 2, "share": 1 }] }, ...]` and omit `amount`'s participant fields — items must sum to `amount`.

Every payer and participant/assignment user id must be an active member of the group (`400 INVALID_EXPENSE_PARTICIPANT` otherwise).

**Response** `201 Created`
```json
{
  "id": 1,
  "groupId": 1,
  "description": "Dinner",
  "amount": 100,
  "currency": "INR",
  "convertedAmount": 100,
  "exchangeRate": 1,
  "category": "Food",
  "receiptUrl": null,
  "splitType": "EQUAL",
  "createdByUserId": 1,
  "createdByName": "Jane Doe",
  "payers": [
    { "userId": 1, "fullName": "Jane Doe", "amountPaid": 100 }
  ],
  "splits": [
    { "userId": 1, "fullName": "Jane Doe", "amountOwed": 50, "percentage": null },
    { "userId": 2, "fullName": "Bob Smith", "amountOwed": 50, "percentage": null }
  ],
  "items": [],
  "createdAt": "2026-08-31T09:00:00",
  "updatedAt": "2026-08-31T09:00:00"
}
```
`items` is only populated when `splitType` is `ITEMIZED`; `percentage` on a split entry is only populated when `splitType` is `PERCENTAGE`.

Fails with `400 INVALID_SPLIT` if the split math doesn't reconcile (wrong field for the chosen type, percentages don't sum to 100, amounts don't sum to `amount`, duplicate user id, empty participants/items) or `400 INVALID_CURRENCY` if `currency` doesn't match the group's default.

---

### `GET /groups/{groupId}/expenses`
Lists all active (non-deleted) expenses for the group. Caller must be an active member.

**Response** `200 OK` — array of the same shape as the create response.

---

### `GET /expenses/{expenseId}`
Full expense detail, including splits and (for `ITEMIZED`) items with per-user assignment breakdown. Caller must be an active member of the expense's group.

**Response** `200 OK` — same shape as the create response.

---

### `PUT /expenses/{expenseId}`
Full replace — same request shape as create, any split type, doesn't have to match the original. Only the expense's creator (`createdBy`) or a group Admin may edit.

**Request** — same shape as create.

**Response** `200 OK` — same shape as the create response.

Fails with `403 NOT_EXPENSE_OWNER` if the caller is neither the creator nor a group Admin.

---

### `DELETE /expenses/{expenseId}`
Soft-deletes the expense. Only the expense's creator or a group Admin may delete.

**Response** `204 No Content`

Fails with `403 NOT_EXPENSE_OWNER` under the same rule as edit.

---

## Errors specific to Expenses

| `error` | Status | Cause |
|---|---|---|
| `EXPENSE_NOT_FOUND` | 404 | expense id doesn't exist or is soft-deleted |
| `INVALID_SPLIT` | 400 | split/payer math doesn't reconcile, wrong fields for the split type, duplicate user id, empty participants/items |
| `INVALID_EXPENSE_PARTICIPANT` | 400 | a payer/participant/assignment user id isn't an active member of the group |
| `NOT_EXPENSE_OWNER` | 403 | caller is neither the expense's creator nor a group Admin, on edit/delete |
| `INVALID_CURRENCY` | 400 | `currency` isn't a real ISO 4217 code, or doesn't match the group's `defaultCurrency` |
