# Business data model (conceptual)

Business Manager persists operational rows under **`bm_owned_row`** semantics—per-user ownership enforced in API handlers.

## Mental entities

- **Time entries** — clock events + manual rows.
- **Money events** — income/expense categories.
- **Clients / inventory** — depending on shipped module breadth.

## Why server ownership matters

Clients can be tampered with; **D1** checks tie rows to authenticated `user_id`.

## Reporting

Mobile generates **PDF exports** client-side (jsPDF) for snapshots—treat as **user artifact**, not canonical accounting records without CPA review.

## Related reading

- [../02-products/business-manager.md](../02-products/business-manager.md)
