# Backend batch — Reminders scheduler (verifies Task Completions in passing) + harness Group

> **Backend repo only.** Reuse centralized `PermissionChecker` + `AuthErrorHandler`. Follow the ERD/Data Dictionary — do NOT invent schema.
> **Per policy, not "done" without its harness Group.** Tests MUST mock dispatch — never send real SMS.

---

## Phase 0 — Gate (STOP + report if unresolved)

> **Signed off by Chief, 2026-09-01:** items 2, 3 and 4 below are **decided** — build them as written, do not re-open them. Item 1 is still a read-and-report step: the contract is stated here, but you confirm it against the merged code.

1. **Task Completions contract (the dependency — this also verifies TC):** read the **merged** Task Completions code + schema (on `main`, commits `92be19b` + `1aa02a7`). Report **exactly how "outstanding / overdue" is determined** — a query, a field, or derived. Expected, per the TC batch: the query is **`dailyTasks(cycleId, date)`**, and outstanding = a `daily_tasks` template exists ∧ no `task_completions` row for `(task_id, date)` with status `DONE`. **If the merged code does not match that → STOP and report.** Reminders cannot be built on an ambiguous contract.
2. **Reminders/notification schema — DECIDED:** the V1 `reminders` table cannot carry idempotency. Write a **migration** adding `recipient_user_id`, `sent_at`, `reminder_date`, and `UNIQUE(task_id, reminder_date, recipient_user_id, channel)`. This **changes the ERD** — that is approved; regenerate the ERD/Data Dictionary from the live DB after the migration (do not hand-edit them).
3. **Recipients — DECIDED:** `daily_tasks.assigned_role_id` is null on every generated task, so do **not** key off the assignee. Recipients = **the farm's members who hold `mark_task_done`** (the same permission TC gates completion on). Resolve per farm, through the centralized scoping.
4. **Channels — DECIDED:** channel stays the schema's `PUSH`/`SMS`; the **provider** sits behind it — **SMS → Africa's Talking**, **PUSH → AWS Pinpoint** (`users.push_token` already exists; `SmsSender` + `LoggingSmsSender` is the stub to build on). Send on **every channel available for that recipient** — both are intentional, not fallback. Dispatch MUST be behind a **mockable interface** (tests must not hit the network).
5. **Timezone:** Dodoma is EAT (UTC+3) — reminders fire at a sensible local hour, not UTC.

Item 1 is now the only gate: proceed once it resolves.

---

## Build (only if Phase 0 resolves)

- **Selection service:** find outstanding/overdue tasks per farm — a plain method **separate from the `@Scheduled` trigger**, so it is unit-testable without the clock.
- **`@Scheduled` trigger:** configurable cron; calls the selector, resolves recipients, dispatches via the channel interface(s).
- **Idempotent send-log:** unique `(task/date, recipient, channel)` so re-ticks and restarts do **not** double-send.
- **Farm-scoped:** the scheduler runs across farms but must not leak tasks across farms (centralized scoping; a per-farm iteration, not a global query that ignores farm).
- **Resilience:** one farm's dispatch failure is logged and skipped — it must not abort the whole tick.
- **Config:** send window / quiet hours; which channels are enabled.

## Tests — harness Group (required; **mock dispatch, no real SMS/Pinpoint**)

Write-path against the harness (throwaway DB, seeder fixture), dispatch mocked:

- **Selection:** given TC state (some done, some outstanding, some overdue), the selector returns exactly the outstanding/overdue ones — never the done.
- **Recipients** resolved per the model.
- **Idempotency:** run the tick twice → dispatch invoked **once** per `(task, recipient, channel)`; the send-log blocks the second.
- **Both channels:** with both enabled, both are dispatched (not fallback).
- **Cross-tenant:** a farm-27 tick does not include farm-26 tasks.
- Assert on the **mocked dispatch interactions**, not the network.
- `mvn test` full suite green — paste counts per Group.

## Report

Phase 0 findings — **especially the TC outstanding/done contract exactly as read from the merged code** (this doubles as TC verification); anything that made you STOP; per-commit changes + acceptance evidence (curl/SQL + test counts); commit hashes; push the branch.

**Out of scope:** Finance, all frontend, real SMS sends, the parked errorCode drift map.
