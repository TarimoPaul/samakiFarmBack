# Backend batch — Reminders scheduler (verifies Task Completions in passing) + harness Group

> **Backend repo only.** Reuse centralized `PermissionChecker` + `AuthErrorHandler`. Follow the ERD/Data Dictionary — do NOT invent schema.
> **Per policy, not "done" without its harness Group.** Tests MUST mock dispatch — never send real SMS.

---

## Phase 0 — Gate (STOP + report if unresolved)

1. **Task Completions contract (the dependency — this also verifies TC):** read the **merged** Task Completions code + schema. Report **exactly how "outstanding / overdue" is determined** — a query, a field, or derived. This is the input Reminders consumes. **If TC does not expose a clear outstanding/done signal → STOP and report.** Reminders cannot be built on an ambiguous contract.
2. **Reminders/notification schema:** from the ERD — a send-log (recipient, channel, task ref, status, `sent_at`). **STOP if unspecified.**
3. **Recipients:** who is reminded for a given task — all farm members, a role, an assignee? From the ERD/model.
4. **Channels:** is there an existing Africa's Talking + AWS Pinpoint abstraction, or do you build the interface? Dispatch MUST be behind a **mockable interface** (tests must not hit the network). Both channels are intentional (not fallback).
5. **Timezone:** Dodoma is EAT (UTC+3) — reminders fire at a sensible local hour, not UTC.

Proceed only if 1–3 resolve.

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
