# Settlements & Balances API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

## How balances work

Nothing here is stored — every balance is computed fresh, on read, from `expense_payers` + `expense_splits` + `settlements`. A settlement is a real-world repayment (cash, Venmo, etc) a user logs to reduce a balance that was created by expenses; deleting one is a full undo, since nothing downstream was cached from it.

For each user: `net = Σ(amountPaid on expenses) − Σ(amountOwed on expenses) + Σ(settlements where they're paidBy) − Σ(settlements where they're paidTo)`. Positive means the group owes them; negative means they owe the group. A settlement pushes both parties' balances toward zero — it's not a new debt, it's the record that an existing one shrank.

Two balance views exist:
- **`GET /groups/{id}/balances`** — one net number per member. Good for a headline figure ("you're owed ₹150 overall"), not for "who specifically."
- **`GET /groups/{id}/balances/simplified`** — the recommended primary view for "who do I pay." Runs a greedy debt-simplification pass (largest creditor matched against largest debtor, repeat) over the net balances above, producing the fewest possible payments that would settle the whole group. This can route through people you never directly transacted with (e.g. A owes B 10, B owes C 10 collapses to a single suggested "A pays C 10," skipping B entirely, since B's net is already zero) — it's a settle-up suggestion, not a literal transaction history.

There's currently no endpoint for "what specifically do I owe a particular person, and why" (a real pairwise, per-relationship breakdown with traceable history) — that would need a different underlying computation (a genuine pairwise ledger rather than net-per-person) and was deliberately scoped out for now in favor of the simpler always-simplified view above.

---

### `POST /groups/{groupId}/settlements`
Logs a settlement — a real-world repayment between two active group members. Caller must be an active member, but doesn't have to be either party (any member can log a settlement they witnessed, same permissiveness as expenses allowing any member to add one involving other payers).

**Request**
```json
{
  "paidByUserId": 2,
  "paidToUserId": 1,
  "amount": 50,
  "currency": "INR",
  "note": "Cash at dinner"
}
```
`note` is optional. `paidByUserId` and `paidToUserId` must both be active members of the group and must differ from each other. `currency` must match the group's `defaultCurrency`, validated the same way as expense currency.

**Response** `201 Created`
```json
{
  "id": 1,
  "groupId": 1,
  "paidByUserId": 2,
  "paidByName": "Bob Smith",
  "paidToUserId": 1,
  "paidToName": "Jane Doe",
  "amount": 50,
  "currency": "INR",
  "note": "Cash at dinner",
  "createdByUserId": 2,
  "createdByName": "Bob Smith",
  "createdAt": "2026-08-31T10:35:06",
  "updatedAt": "2026-08-31T10:35:06"
}
```
`createdByUserId` tracks who logged it, independent of `paidByUserId`/`paidToUserId` — this is who edit/delete authorization checks against.

Fails with `400 INVALID_SETTLEMENT` if `paidByUserId == paidToUserId`, `400 INVALID_SETTLEMENT_PARTICIPANT` if either isn't an active group member, or `400 INVALID_CURRENCY` if `currency` doesn't match the group's default.

---

### `GET /groups/{groupId}/settlements`
Lists all active (non-revoked) settlements for the group. Caller must be an active member.

**Response** `200 OK` — array of the same shape as the create response.

---

### `GET /settlements/{settlementId}`
Settlement detail. Caller must be an active member of the settlement's group.

**Response** `200 OK` — same shape as the create response.

---

### `PUT /settlements/{settlementId}`
Full replace — same request shape as create. Only the settlement's logger (`createdBy`) or a group Admin may edit.

**Request** — same shape as create.

**Response** `200 OK` — same shape as the create response.

Fails with `403 NOT_SETTLEMENT_OWNER` if the caller is neither the logger nor a group Admin.

---

### `DELETE /settlements/{settlementId}`
Revokes (soft-deletes) the settlement — a full undo, since balances are always computed fresh and nothing was cached from it. Only the logger or a group Admin may revoke.

**Response** `204 No Content`

Fails with `403 NOT_SETTLEMENT_OWNER` under the same rule as edit.

---

### `GET /groups/{groupId}/balances`
Net balance per active member. Caller must be an active member.

**Response** `200 OK`
```json
[
  { "userId": 1, "fullName": "Jane Doe", "netBalance": 200.00 },
  { "userId": 2, "fullName": "Bob Smith", "netBalance": -100.00 },
  { "userId": 3, "fullName": "Charlie Lee", "netBalance": -100.00 }
]
```
A member who's left the group but has historical expense/settlement activity still appears here — a real debt doesn't disappear when someone leaves.

---

### `GET /groups/{groupId}/balances/simplified`
The recommended primary "who do I pay" view — minimum set of payments that would settle the whole group. Caller must be an active member.

**Response** `200 OK`
```json
[
  { "fromUserId": 2, "fromName": "Bob Smith", "toUserId": 1, "toName": "Jane Doe", "amount": 100.00 }
]
```

---

## Errors specific to Settlements

| `error` | Status | Cause |
|---|---|---|
| `SETTLEMENT_NOT_FOUND` | 404 | settlement id doesn't exist or is revoked |
| `INVALID_SETTLEMENT` | 400 | `paidByUserId` equals `paidToUserId` |
| `INVALID_SETTLEMENT_PARTICIPANT` | 400 | `paidByUserId`/`paidToUserId` isn't an active member of the group |
| `NOT_SETTLEMENT_OWNER` | 403 | caller is neither the logger nor a group Admin, on edit/delete |
| `INVALID_CURRENCY` | 400 | `currency` isn't a real ISO 4217 code, or doesn't match the group's `defaultCurrency` |
