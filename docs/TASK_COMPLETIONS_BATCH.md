# Backend batch — Task Completions module (GraphQL) + harness Group

> **Delivery:** drop into `spring-backend/docs/` and tell the agent to READ it. Do NOT paste.
> **Backend repo only.** Reuse Feed/WQ pattern + centralized `PermissionChecker` + `AuthErrorHandler`.
> **Per policy, not "done" without its harness Group.**
> **IMPORTANT — build on what already exists (confirmed via the Reminders Phase 0 probe):**

## What is already merged (V1) — do NOT recreate it
- Table **`task_completions`** exists: `status` VARCHAR (PENDING/DONE/MISSED/LATE), `completed_at`, `completed_by_user_id`, **`UNIQUE(task_id, completion_date)`**. No Java mapping — you add it.
- Permission **`mark_task_done`** is seeded (OWNER/FARM_MANAGER/WORKER) — consumed by nothing yet. Use it; do not add a new one.
- **`daily_tasks`** rows are created by `CycleService.createDefaultTasks` — 3 per cycle (feed 07:00, feed 17:00, water 08:00), `frequency=DAILY`, `scheduled_time`=time-of-day, **no due date**, `assigned_role_id` left **null**. It is a recurring **template**, not per-day rows.

## The completion model is therefore fixed by the schema
- A completion is **one row per `(task_id, completion_date)`** with a status — NOT a status flip on `daily_tasks`. The UNIQUE constraint already dictates this.
- **"Outstanding on date D"** = a `daily_task` template exists for the cycle **AND** there is no `task_completions` row for `(task_id, D)` with status `DONE`.

## Phase 0 — confirm, then build
1. Confirm the above against the live schema/code. If the table/permission differ from this, STOP and report.
2. Confirm how a `daily_task` reaches a farm (via `cycles.unit_id → production_units.farm_id`) for scoping.

## Build — GraphQL (Tasks module is GraphQL)
- **Mutation:** complete a task for a date → upsert a `task_completions` row (status DONE, `completed_at`, `completed_by`). Farm-scoped via **centralized** `PermissionChecker` (gate on `mark_task_done`).
- **Double-complete** on the same `(task_id, date)` → the UNIQUE blocks it → `CONFLICT` with `errorCode`.
- **Query (the Reminders contract — required):** for a cycle + date, return each daily task with **outstanding vs DONE** resolved by the derivation above. Make this explicit and queryable; Reminders depends on it.
- **Scoping/validation:** completing a non-existent / other-farm / other-cycle task → `FORBIDDEN`/`NOT_FOUND`/`VALIDATION_ERROR` with `errorCode`. Cross-tenant = D-1 proof.
- Do **not** alter `daily_tasks` generation here. **Flag** (do not fix) that `assigned_role_id` is null on default tasks — Reminders will need a decision on it.

## Tests — harness Group (required)
Write-path, seeded principals, throwaway DB:
- WORKER completes a task → `task_completions` row (SQL before/after).
- double-complete same `(task,date)` → `CONFLICT`.
- VIEWER → `FORBIDDEN`; no-role → `FORBIDDEN`.
- **D-1:** farm-27 principal completing farm-26's task → `FORBIDDEN`, both directions.
- **outstanding-vs-DONE query** returns the right set before and after a completion (the Reminders contract).
- `mvn test` full suite green — paste counts.

## Report
Confirmation of the merged schema; per-commit changes + acceptance evidence (curl/SQL + test counts); the exact **outstanding/done query** you exposed; the `assigned_role_id` flag; commit hashes; push the branch.

**Out of scope:** Reminders, Finance, frontend, the parked drift map. Do not recreate the V1 table or the permission.
