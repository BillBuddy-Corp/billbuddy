# Bill Scanner API

Base path: `/api/v1`. All endpoints below require **Auth required** (`Authorization: Bearer <accessToken>`).

Interactive docs (always in sync with the code): `/swagger-ui.html` once the backend is running.

Upload the receipt image first via `POST /files` (see `storage.md`), then scan it. The scan result is meant to pre-fill an `ITEMIZED` expense (`expenses.md`), not create one directly.

---

## How receipt scanning works

**Upload then scan, in two steps**, same pattern as Storage's upload-then-attach. There's no dedicated upload endpoint here: `POST /files` returns an `id`, then `POST /files/{id}/scan-receipt` reads that file. You can only scan a file you uploaded yourself.

**Claude reads, Java computes.** The model (Claude, via vision + tool-use) only transcribes and identifies what's printed on the receipt: each item's price, a discount printed directly under one item, a quantity multiplier if one's printed, whole-receipt discounts, and vouchers. It never does arithmetic. All the math, netting an item's own discount into its price, merging lines that are the same item printed twice, and scaling item prices to sum exactly to the real total, happens deterministically in the backend. This is a direct response to live-testing: the model reads and classifies reliably but is not reliable at doing consistent math across many line items on one receipt.

**`sum(items) == amount` is guaranteed**, always, for every successful scan. That guarantee is what makes a scan directly usable to pre-fill an `ITEMIZED` expense's items.

**Two independent review flags**:
- `needsReview` is `true` only when that sum guarantee itself couldn't be established (extraction returned nothing usable, or the numbers were too inconsistent to reconcile at all). This is the one that actually matters for whether the item prices can be trusted.
- `discountsNeedReview` is a separate signal for when the informational breakdown (`subtotal`, `otherDiscount`) doesn't add up to `amount` on its own, for example a receipt whose discount is also printed as a rollup total elsewhere, or sales tax the model has no field for. It does **not** imply the item prices are wrong; `needsReview` already covers that independently.

**Scans are cached by file.** Re-scanning an already-scanned file returns the same stored result instantly, with no repeat call to the model.

---

### `POST /files/{fileId}/scan-receipt`
Scans an already-uploaded receipt image. Caller must be the file's uploader.

**Request**: no body, `fileId` is a path variable.

**Response** `200 OK`
```json
{
  "fileId": 42,
  "merchant": "Tesco Ireland - Parnell Street Metro",
  "amount": 37.67,
  "currency": "EUR",
  "transactionDate": null,
  "otherDiscount": 7.01,
  "voucherAmount": null,
  "subtotal": 41.85,
  "needsReview": false,
  "discountsNeedReview": true,
  "items": [
    { "name": "Tesco Mild Onions 3 Pack", "amount": 1.33, "quantity": 2 },
    { "name": "Tesco Basmati Rice 2kg", "amount": 2.65, "quantity": 1 }
  ]
}
```
Any field the model couldn't confidently read (`merchant`, `amount`, `currency`, `transactionDate`, `otherDiscount`, `voucherAmount`) comes back `null` rather than a guessed value, this is expected and not itself a failure. `subtotal` is the sum of item prices before the whole-receipt discount is proportionally applied, useful for seeing the size of that discount; `otherDiscount` is the sum of every whole-receipt discount line printed separately from any item.

Fails with `400 RECEIPT_SCAN_FAILED` if the image doesn't look like a readable receipt at all, `403 NOT_FILE_OWNER` if you didn't upload the file, or `404 STORED_FILE_NOT_FOUND` if the file id doesn't exist.

---

## Errors specific to Bill Scanner

| `error` | Status | Cause |
|---|---|---|
| `RECEIPT_SCAN_FAILED` | 400 | the image isn't a readable receipt, or the scanning service couldn't be reached |
