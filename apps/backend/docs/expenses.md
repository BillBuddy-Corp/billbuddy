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

`expense_splits` is always the final answer regardless of split type — `sum(splits.amountOwed) == amount` in the expense's own currency (that's intentional bookkeeping, not a self-debt; net balance = paid − owed, computed later by Balances). Payer and split amounts are persisted converted into the group's base currency, using `exchangeRate` — for a same-currency expense (the common case) this is a no-op, but for a cross-currency expense each line is rounded independently after conversion, so the stored `sum(splits.amountOwed)` can differ from `convertedAmount` by a cent or two on a multi-way split; balances/settlements always read the stored (converted) amounts, so this never leaves anyone short, it's the same kind of rounding-dust tolerance every splitting app has.

**Payers**: `payers` is always required (at least one). `sum(payers.amountPaid)` must equal `amount`. Multiple payers per expense are supported.

**Currency**: `currency` doesn't have to match the group's `defaultCurrency` — any real ISO 4217 code works. If it differs from the group's default, `exchangeRate` is required (a positive number, the multiplier from the expense's currency to the group's currency) and `convertedAmount = amount * exchangeRate`; use `GET /groups/{groupId}/expenses/exchange-rate` to get a live suggested rate first. If `currency` matches the group's default, `exchangeRate` is ignored and treated as `1`.

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
`exchangeRate` is omitted here since `currency` matches the group's default; if it didn't (e.g. `"currency": "THB"` in an INR group), add `"exchangeRate": 2.5` (or whatever rate `GET .../exchange-rate` suggested, or whatever the user confirms/overrides).

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

Fails with `400 INVALID_SPLIT` if the split math doesn't reconcile (wrong field for the chosen type, percentages don't sum to 100, amounts don't sum to `amount`, duplicate user id, empty participants/items) or `400 INVALID_CURRENCY` if `currency` isn't a real code, or differs from the group's default with no positive `exchangeRate` given.

---

### `GET /groups/{groupId}/expenses`
Lists all active (non-deleted) expenses for the group. Caller must be an active member.

**Response** `200 OK` — array of the same shape as the create response.

---

### `GET /groups/{groupId}/expenses/exchange-rate`
Suggests a live exchange rate from `fromCurrency` to the group's default currency, for the client to prefill and let the user confirm or override before creating/editing an expense. Caller must be an active member.

**Request** — query param `fromCurrency` (e.g. `?fromCurrency=THB`).

**Response** `200 OK`
```json
{
  "fromCurrency": "THB",
  "toCurrency": "INR",
  "rate": 2.51,
  "asOf": "2026-09-04"
}
```
If `fromCurrency` already equals the group's default currency, this short-circuits to `rate: 1` without calling any external service. Rates come from [Frankfurter](https://frankfurter.dev), a free, keyless API backed by ECB reference rates covering major world currencies — this is always just a suggestion, the actual `exchangeRate` used on the expense is whatever the client submits.

Fails with `400 INVALID_CURRENCY` if `fromCurrency` isn't a real code, or `400 FX_RATE_LOOKUP_FAILED` if no rate is available for that pair (an unsupported currency, or the upstream service is unreachable) — the client should fall back to letting the user type a rate in manually.

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
| `INVALID_CURRENCY` | 400 | `currency`/`fromCurrency` isn't a real ISO 4217 code, or `currency` differs from the group's `defaultCurrency` with no positive `exchangeRate` given |
| `FX_RATE_LOOKUP_FAILED` | 400 | no live exchange rate available for the requested currency pair |
