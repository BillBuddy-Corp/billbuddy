# Recurring Expenses API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

## How it works

A recurring expense is a template, not an expense itself. It captures everything a normal expense needs (description, amount, currency, split) plus a schedule (`WEEKLY` anchored to a day of the week, or `MONTHLY` anchored to a day of the month), and a background job checks once a day for templates that are due and creates a real expense from each one automatically, through the exact same creation path (and validation) as `POST /groups/{groupId}/expenses` — see `expenses.md` for the split-type/currency rules, which apply identically here.

A `MONTHLY` template anchored past the end of a shorter month clamps to that month's last day (day 31 in February becomes the 28th or 29th).

The acting creator of every auto-generated expense is whoever created the template. If they leave the group before a cycle runs, that cycle is skipped (logged, not retried) rather than blocking or failing loudly — the schedule always advances to the next occurrence regardless of whether generation succeeded.

There's no in-place editing for now — change a recurring expense's amount or split by cancelling it and creating a new one.

---

### `POST /groups/{groupId}/recurring-expenses`
Creates a recurring expense template. Caller must be an active group member.

**Request** (EQUAL example, monthly)
```json
{
  "description": "Rent",
  "amount": 15000,
  "currency": "INR",
  "category": "Housing",
  "splitType": "EQUAL",
  "payers": [
    { "userId": 1, "amountPaid": 15000 }
  ],
  "participantUserIds": [1, 2],
  "frequency": "MONTHLY",
  "dayOfMonth": 1
}
```
For a `WEEKLY` template, replace `frequency`/`dayOfMonth` with `"frequency": "WEEKLY", "dayOfWeek": 1` (1 = Monday .. 7 = Sunday). `percentages`/`exactAmounts`/`items` work the same as expense creation, depending on `splitType`. There's no `receiptFileId` here — a receipt belongs to one occurrence, attach it afterward on the generated expense via `PUT /expenses/{expenseId}`.

**Response** `201 Created`
```json
{
  "id": 1,
  "groupId": 10,
  "description": "Rent",
  "amount": 15000,
  "currency": "INR",
  "exchangeRate": null,
  "category": "Housing",
  "splitType": "EQUAL",
  "payers": [{ "userId": 1, "amountPaid": 15000 }],
  "participantUserIds": [1, 2],
  "percentages": null,
  "exactAmounts": null,
  "items": null,
  "frequency": "MONTHLY",
  "dayOfWeek": null,
  "dayOfMonth": 1,
  "nextRunAt": "2026-10-01",
  "active": true,
  "createdByUserId": 1,
  "createdByName": "Jane Doe",
  "createdAt": "2026-09-06T09:00:00",
  "updatedAt": "2026-09-06T09:00:00"
}
```
`nextRunAt` is always the next occurrence strictly after today — creating a template never generates an expense immediately, even if today happens to match the pattern.

Fails with `400 INVALID_SPLIT` under the same rules as expense creation, `400 INVALID_CURRENCY` if `currency` isn't a real code or differs from the group's default with no positive `exchangeRate`, or `400 INVALID_RECURRENCE` if `dayOfWeek` is missing for `WEEKLY` or `dayOfMonth` is missing for `MONTHLY`.

---

### `GET /groups/{groupId}/recurring-expenses`
Lists all non-cancelled (active and paused) templates for the group. Caller must be an active member.

**Response** `200 OK` — array of the same shape as the create response.

---

### `POST /recurring-expenses/{templateId}/pause`
Stops future auto-generation until resumed. Only the template's creator or a group admin may pause.

**Response** `200 OK`

Fails with `403 NOT_RECURRING_EXPENSE_OWNER` if the caller is neither the creator nor a group admin.

---

### `POST /recurring-expenses/{templateId}/resume`
Resumes future auto-generation.

**Response** `200 OK`

Fails with the same `403` rule as pause.

---

### `DELETE /recurring-expenses/{templateId}`
Soft-deletes (cancels) the template, stopping all future generation. Expenses already generated from it are untouched.

**Response** `204 No Content`

Fails with the same `403` rule as pause.

---

## Errors specific to Recurring Expenses

| `error` | Status | Cause |
|---|---|---|
| `RECURRING_EXPENSE_NOT_FOUND` | 404 | template id doesn't exist or is cancelled |
| `INVALID_RECURRENCE` | 400 | `dayOfWeek` missing for `WEEKLY`, or `dayOfMonth` missing for `MONTHLY` |
| `NOT_RECURRING_EXPENSE_OWNER` | 403 | caller is neither the template's creator nor a group admin, on pause/resume/cancel |
