# REPO_AUDIT — authoritative BUILT / PARTIAL / MISSING inventory

**Read-only inventory.** Nothing was built, fixed, migrated or merged to produce this
document. Every verdict below was derived from the code and schema at the refs named
here, not from `GAP_ANALYSIS.md`, `FIX_BATCH_REPORT.md`, `FRONTEND_BACKEND_AUDIT.md`, or
any other prior report — those are listed at the end as artifacts, not as evidence.

## Refs audited

| Repo | Ref | HEAD | Date |
|---|---|---|---|
| `spring-backend` | `main` | `37ed20deffe8edf0442df61d5c8b1eb8c266bdf2` | 2026-09-01 21:11 +0300 |
| `samakiFarmFront` | `main` | `74b1a1f32b382d82ddad0a8b9b8456f7cc96c166` | 2026-09-01 11:48 +0300 |

The backend working tree is checked out on `feat/reminders-scheduler` (`55717cb`), **not**
on `main`. `main` was read through `git show main:<path>` rather than by switching
branches, so nothing in the working tree was touched.

Migrations present on `main`: **V1 … V11**. `V12__reminders_send_log.sql` exists only on
`feat/reminders-scheduler`.

---

## 1. Schema reality check — what the ERD actually creates

`V1__init_schema.sql` creates **20 tables**; `V3` adds a 21st (`password_reset_otps`).
The brief's "17 tables" undercounts by the junction/system tables
(`role_permissions`, `farm_users`, `password_reset_otps`) and `customers`.

Full list: `roles`, `permissions`, `role_permissions`, `users`, `farms`, `farm_users`,
`assets`, `production_units`, `species`, `cycles`, `feed_purchases`, `feeding_logs`,
`feed_stock_movements`, `water_quality_logs`, `daily_tasks`, `task_completions`,
`reminders`, `costs`, `customers`, `sales`, `password_reset_otps`.

**Four tables have zero Java on `main`** — no entity, no repository, no reference of any
kind (`git grep -il <name> main -- src/main/java` returns 0 files):

- `assets`
- `costs`
- `customers`
- `sales`

`reminders` also has no Java on `main`; it has a full implementation on
`feat/reminders-scheduler` (see §9).

---

## 2. Group A — RBAC, identity, session

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `roles` | V1:9, V2:25 | `rbac/entity/Role.java` | `RoleService`, `RbacSeedService` | `RoleController` GET/POST `/api/roles`, PUT `/api/roles/{id}/permissions` (`:32,:38,:45`) | `manage_users` seeded (CSV) / consumed `RoleController.java:33,39,46,58` | `AuthRegressionTest:210-270` "RBAC inayohaririwa wakati wa run (D-13)" — real PUT write path | **BUILT** | — |
| `permissions` | V1:15, V2:33 | `rbac/entity/Permission.java` | `RbacDataInitializer`, `RbacSeedService` | GET `/api/roles/permissions` (`RoleController.java:57`) | n/a (is the catalogue) | `AuthRegressionTest:229` reads via `permissionIdsFor` | **BUILT** | Read-only by design; seeded from `seed/permissions.csv` + V7/V8/V10 inserts |
| `role_permissions` | V1:23 | mapped as `@ManyToMany` on `Role` (no own entity — correct) | `RoleService.updateRolePermissions` | PUT `/api/roles/{id}/permissions` | — | `AuthRegressionTest:221,239,257` (add, remove, reject-unknown-whole) | **BUILT** | — |
| `users` | V1:29, V4, V5, V6 | `user/entity/User.java`, `UserStatus.java` | `UserService`, `AuthService` | `UserController` — 9 endpoints (`:38,44,52,59,65,71,79,87,95,104`) | `manage_users`, `approve_users` seeded / both consumed via `@PreAuthorize` | `AuthRegressionTest`, `MembershipConflictRegressionTest` | **PARTIAL** | `POST /api/auth/register`, `POST /api/users` (admin create), `POST /{id}/approve`, `/disable`, `/enable`, `DELETE /{id}` have **no test touching them** — tests only hit `login`, `me`, `change-password`, `/api/users/{id}/memberships*` |
| `farm_users` | V1:50, V5:33 | `farmuser/entity/FarmUser.java` | `FarmUserService` | POST/PUT/DELETE `/api/users/{id}/memberships*` | `manage_users` consumed | `MembershipConflictRegressionTest` (8 tests: OWNER_IMMUTABLE, duplicate CONFLICT, role change, two-tier scoping) | **BUILT** | — |
| `password_reset_otps` | V3:7 | `auth/entity/PasswordResetOtp.java` | `PasswordResetService` | POST `/api/auth/forgot-password`, `/reset-password` (`AuthController.java:62,69`) | none (unauthenticated by design) | **none** — `git grep` over `src/test` finds only `auth/login`, `auth/me`, `auth/change-password` | **PARTIAL** | No test on the OTP write path at all; `LoggingSmsSender` is the only `SmsSender` impl |
| Session / JWT | — | — | `JwtUtil`, `JwtAuthFilter`, `PermissionChecker`, `CustomPermissionEvaluator` | POST `/api/auth/login`, GET `/api/auth/me`, POST `/api/auth/change-password` | — | `AuthRegressionTest:25-208` — login, 401 vs 403, `must_change_password` gate both directions, `/me` returns the real permission set | **BUILT** | — |

**Group verdict: 5 BUILT / 2 PARTIAL.**

---

## 3. Group B — Farms and scope

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `farms` | V1:42, V2:18, V9 (`uq_farms_name`) | `farm/entity/Farm.java` | `FarmService` | `FarmController` POST + GET `/api/farms` (`:23,:29`) | `manage_farms` seeded V7:11 / consumed `FarmController.java:24,30` and as `COMPANY_WIDE_PERMISSION` in `PermissionChecker.java:35` | **no backend test hits `/api/farms`** — the string never appears in `src/test` | **PARTIAL** | Missing write-path test for farm creation and for the V9 name-UNIQUE conflict branch. No update/delete endpoint (may be intentional) |
| Farm scoping (cross-cutting) | — | — | `PermissionChecker.requireFarmScope` / `requireResourceInCallersFarm` (`:80-120`) | consumed by every GraphQL service | — | D-1 leak suites in `FeedRegressionTest:156`, `WaterQualityRegressionTest:80`, `DailyTaskCompletionRegressionTest:319`; two-tier scoping in `MembershipConflictRegressionTest:121,132` | **BUILT** | — |

**Group verdict: 1 BUILT / 1 PARTIAL.**

---

## 4. Group C — Assets and production units

> **Resolves the brief's "what creates `production_units`?"** — exactly one writer:
> `createProductionUnit` (GraphQL), gated on `manage_units`. Plus `DevSeedService.unit()`
> under `@Profile("dev")` only.

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `assets` | V1:58 | **none** | **none** | **none** | none | none | **MISSING** | Table only. Zero Java references anywhere in the repo |
| `production_units` | V1:68, V2:57 | `productionunit/entity/ProductionUnit.java` (+ `UnitType` enum) | `ProductionUnitService` (`listForCurrentFarm`, `create`) | `ProductionUnitResolver` — Query `productionUnits`, Mutation `createProductionUnit` | `manage_units` seeded (CSV) / consumed `ProductionUnitService.java:38`; read gated on `view_dashboard` (`:33`) | **none** — `createProductionUnit` does not appear in `src/test`; the fixture units `unitA`/`unitB` come from `DevSeedService`, written directly | **PARTIAL** | No write-path test. **No update, no delete, no status transition** — `status` is set to `IDLE` on create (`:52`) and to `ACTIVE` by `CycleService.create` (`:113`), and is **never returned to `IDLE` by anything**, so a unit whose cycle ends stays ACTIVE forever |

**Group verdict: 0 BUILT / 1 PARTIAL / 1 MISSING.**

---

## 5. Group D — Species, cycles, stocking, harvest

> **Resolves "Species + Stocking"** — there is no separate stocking table. Stocking lives
> on `cycles`: `stocking_date`, `fingerlings_count`, `survival_rate_estimate`,
> `species_id`. Density is **not** recorded anywhere (`size_m3` exists on the unit but is
> never combined with `fingerlings_count` in any code path).
>
> **Resolves "Harvest"** — **no harvest table, no harvest module, no harvest endpoint.**
> The only harvest artifacts are the two `cycles` columns (`expected_harvest_date`, written
> by `CycleService`; `actual_harvest_date`, **written by nothing**) and
> `species.avg_harvest_weight_kg`, which is read by no code.
>
> **Resolves "cycle status lifecycle"** — there is **no lifecycle**. `CycleService.create`
> hard-sets `status = "ACTIVE"` (`:110`). `STATUSES = {ACTIVE, HARVESTED, FAILED}` (`:52`)
> is used **only to validate the `cycles(status:)` query filter**, never to move a cycle
> between states. Nothing closes, harvests or fails a cycle. The module is exactly
> `createCycle` + default-task generation + a filtered list.

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `species` | V1:80, V2:65, seeded V1:223 (Sato, Kambale) | `species/entity/Species.java` | `SpeciesService.listAll` | `SpeciesResolver` — Query `species` | read gated on `view_dashboard` (`SpeciesService.java:45`) | **none** — no test queries `species`; tests read it via `speciesRepository` directly (`DailyTaskCompletionRegressionTest:76`) | **PARTIAL** | Read-only catalogue by explicit design (no create/edit). Missing a test on the query and its permission gate |
| `cycles` | V1:87, V2:41 | `cycle/entity/Cycle.java` | `CycleService` (`create`, `listForCurrentFarm`, `expectedHarvestDate`) | `CycleResolver` — Query `cycles(status:)`, Mutation `createCycle` | `edit_cycle` seeded (CSV) / consumed `CycleService.java:80`; read `view_dashboard` (`:56`) | `createCycle` is called **once**, as a fixture in `DailyTaskCompletionRegressionTest:78` — with no assertions on it | **PARTIAL** | No test group of its own: the `expectedHarvestDate` arithmetic (incl. the fractional-month path at `:134-152`), the `edit_cycle` gate, the cross-farm `unitId` check (`:87`), and the status-filter rejection (`:66`) are all **untested**. No `updateCycle` / `closeCycle` / `harvestCycle` mutation |
| Stocking | (columns on `cycles`) | — | `CycleService.create` | `createCycle` input | `edit_cycle` | as above | **PARTIAL** | Density (fingerlings ÷ m³) not computed or stored anywhere |
| Harvest (SRS Module 8) | **no table** | **none** | **none** | **none** | **none** | **none** | **MISSING** | Whole module absent. `cycles.actual_harvest_date` has no writer |

**Group verdict: 0 BUILT / 3 PARTIAL / 1 MISSING.**

---

## 6. Group E — Feed

> **Resolves "Feed: logs only, or also inventory/stock?"** — **both.** Purchases, feeding
> logs, and a system-generated stock ledger with a running balance.

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `feed_purchases` | V1:100, V8:13 (`total_cost` is `GENERATED ALWAYS`) | `feed/entity/FeedPurchase.java` | `FeedService.recordPurchase` | Mutation `recordFeedPurchase`, Query `feedPurchases` | `manage_feed_stock` seeded V8:46 / consumed `FeedService.java:87` | `FeedRegressionTest:49,110,125` | **BUILT** | — |
| `feeding_logs` | V1:111, V8:21 | `feed/entity/FeedingLog.java` | `FeedService.logFeeding` | Mutation `logFeeding`, Query `feedingLogs(cycleId:)` | `log_feeding` seeded V8:46 / consumed `FeedService.java:109` | `FeedRegressionTest:66,134,170` | **BUILT** | — |
| `feed_stock_movements` | V1:120, V8:29 | `feed/entity/FeedStockMovement.java` | `FeedService` (writes IN on purchase, OUT on feeding — never client-written) | Query `feedStockMovements`, `feedStockBalance` | read `view_dashboard` (`FeedService.java:57,64,74,82`) | `FeedRegressionTest:38-118` — balance starts 0, IN, OUT, sums, goes negative, DB-computed `totalCost` | **BUILT** | — |

**Group verdict: 3 BUILT.** 22 tests across ledger arithmetic, permission gating, D-1
farm walls, and structural rejection.

---

## 7. Group F — Water quality

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `water_quality_logs` | V1:131, V10:24 (audit + soft-delete), V11:25 (`ammonia`) | `waterquality/entity/WaterQualityLog.java` | `WaterQualityService` | `WaterQualityResolver` — Mutation `logWaterQuality`, Query `waterQualityLogs(unitId:, cycleId:)` | `log_water_quality` seeded V10:44 / consumed `WaterQualityService.java:44`; read `view_dashboard` (`:45`) | `WaterQualityRegressionTest` — 24 tests: write + read-back, D-1 both directions, permission gate, "bad readings are stored" (DO 0.8, pH 4.2, ammonia 0.9), structural rejection (pH 15 / -1, negative O₂/NH₃, overflow) | **BUILT** | — |

**Group verdict: 1 BUILT.**

---

## 8. Group G — Daily tasks and completions

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `daily_tasks` | V1:143, V2:49 | `dailytask/entity/DailyTask.java` | `DailyTaskService`; rows created by `CycleService.createDefaultTasks` (`:155-172`) | Query `dailyTasks(cycleId:, date:)` → `DailyTaskStatus` | read `view_dashboard` (`DailyTaskService.java:67`) | `DailyTaskCompletionRegressionTest:362-492` | **PARTIAL** | Templates are **only** creatable as the three hard-coded defaults ("Kulisha - Asubuhi" 07:00, "Kulisha - Jioni" 17:00, "Kuangalia Maji" 08:00). No mutation to add, edit or remove a task; `assigned_role_id` is never set — pinned by the test at `:469` |
| `task_completions` | V1:152 (`UNIQUE(task_id, completion_date)`) | `dailytask/entity/TaskCompletion.java` | `DailyTaskService` | Mutation `completeTask` | `mark_task_done` seeded (CSV) / consumed `DailyTaskService.java:64` | `DailyTaskCompletionRegressionTest` — 26 tests: write path, double-completion CONFLICT, the UNIQUE itself, D-1, MISSED→DONE, "other day ≠ today" | **BUILT** | — |

`mark_task_done` was the orphan permission named in the brief; it is **no longer
orphaned** — `DailyTaskService.java:64` consumes it and
`DailyTaskCompletionRegressionTest:284,306` pins both sides of the gate.

**Group verdict: 1 BUILT / 1 PARTIAL.**

---

## 9. Group H — Reminders ⚠️ built, but NOT on `main`

`reminders` (V1:163) has **zero Java on `main`**. The full module lives on
`feat/reminders-scheduler` — the only unmerged backend branch.

| Layer | On `main` | On `feat/reminders-scheduler` (`55717cb`) |
|---|---|---|
| Schema | V1 table only | `V12__reminders_send_log.sql` adds recipient, `reminder_date`, `sent_at`, and a UNIQUE |
| Entity | — | `reminder/entity/Reminder.java` |
| Repository | — | `ReminderRepository`, plus new queries on `DailyTaskRepository` (+52 lines) and `FarmUserRepository` (+42) |
| Service | — | `ReminderDispatchService` (227 lines), `OutstandingTaskSelector`, `ReminderRecipientService`, `ReminderSendLog` |
| Scheduler | — | `ReminderScheduler`, `SchedulingConfig`, `ReminderProperties`, `application.yml` (+22) |
| Senders | `SmsSender` / `LoggingSmsSender` only | adds `PushSender` / `LoggingPushSender` |
| API | — | **none — and none intended.** It is a scheduler, not an endpoint |
| Tests | — | `ReminderSchedulerRegressionTest` (621 lines): send-once rule, both channels, farm wall |

**Verdict on `main`: MISSING. Verdict on the branch: BUILT (no API layer, by design).**
This is the clearest "done but not on `main`" item in either repo.

---

## 10. Group I — Finance, customers, sales, reporting

> **Resolves "Finance: `costs`, `sales` — anything at all?"** — **Nothing. Tables only.**
>
> **Resolves "Reporting/analytics: FCR, cost-per-kg, mortality?"** — **No endpoint of any
> kind.** No service, no resolver, no query.

| Table/Module | Schema | Entity | Service | API | Perm (seed / consumed) | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|---|---|
| `costs` | V1:172 + `idx_costs_cycle` | none | none | none | `view_finance` seeded / **consumed by nothing** | none | **MISSING** | Table + index only |
| `customers` | V1:181 | none | none | none | none | none | **MISSING** | Table only |
| `sales` | V1:188 + `idx_sales_cycle` | none | none | none | `view_finance` seeded / **consumed by nothing** | none | **MISSING** | Table + index only |
| Reporting / analytics (FCR, cost-per-kg, mortality) | — | — | none | none | `view_dashboard` exists but only gates raw list reads | none | **MISSING** | No aggregate is computed anywhere on the server. `feedStockBalance` is the only derived number in the whole API |

**Group verdict: 0 BUILT / 4 MISSING.**

---

## 11. Orphans

**Orphan permission — one, and only one:**

- **`view_finance`** — seeded in `seed/permissions.csv` and granted to OWNER and
  FARM_MANAGER in `seed/role_permissions.csv`. Its only appearance in Java is a **comment**
  at `rbac/entity/Permission.java:24`. No `@PreAuthorize`, no `PermissionChecker.require`,
  no test. It gates nothing, because there is nothing to gate (§10).

All ten other permissions are consumed by a real check:
`view_dashboard`, `edit_cycle`, `manage_units`, `mark_task_done`, `manage_farms`,
`log_feeding`, `manage_feed_stock`, `manage_users`, `approve_users`, `log_water_quality`.

**Orphan tables (ERD table with no Java):** `assets`, `costs`, `customers`, `sales`
— and `reminders`, on `main` only.

**Orphan columns worth naming:**

- `cycles.actual_harvest_date` — no writer anywhere.
- `species.avg_harvest_weight_kg` — exposed on the GraphQL type, read by no code.
- `daily_tasks.assigned_role_id` — never set; `DailyTaskCompletionRegressionTest:469`
  asserts it is null for all data.
- `users.push_token` — declared in V1 "kwa reminders"; no writer on `main`.
- `production_units.status` — write-only in practice; nothing ever returns it to `IDLE`.

---

## 12. Backend completeness

Counting the 19 modules across the eight functional groups:

**BUILT on `main` — 6:** Session/JWT auth · RBAC (roles + permissions + role_permissions) ·
Users & memberships · Feed (3 tables) · Water quality · Task completions. Farm scoping is
built and tested as a cross-cutting concern.

**PARTIAL — 8:** Users lifecycle endpoints (untested) · Password reset (untested) ·
Farms (untested) · Production units (no test, no lifecycle) · Species (untested) ·
Cycles (no test group, no lifecycle) · Stocking (no density) · Daily-task templates
(hard-coded only).

**MISSING — 5 (+1 off-main):** Assets · Harvest · Finance/costs · Customers/sales ·
Reporting & analytics. **Reminders is BUILT but unmerged.**

> **Backend: 6 / 19 modules BUILT on `main`** (7 / 19 if `feat/reminders-scheduler` merges).

Test harness: real HTTP + real Postgres, throwaway `samaki_test_*` database per run,
Flyway V1 → latest from empty (`support/TestDatabase.java`, `support/IntegrationTest.java`).
Groups present: Harness smoke, **A** Auth/RBAC, **B** membership, **B** water quality,
**C** feed, **D** daily tasks. **No group covers Farms, Production Units, Cycles or
Species** — the four modules whose write paths have no test.

---

## 13. Frontend cross-check (`samakiFarmFront` @ `74b1a1f`)

**All five feature branches are fully merged into `main`** (`git branch --no-merged main`
returns nothing; each is 0 commits ahead). There is no "done but not on `main`" work on
the frontend side.

Routes — the complete list, from `src/app/app.routes.ts`:

`/login` · `/signup` · `/change-password` · `/dashboard` · `/farms` · `/approvals` ·
`/members` · `**` → login.

| Screen | Route | Component | Guard / gating | Tests | Verdict | Gap |
|---|---|---|---|---|---|---|
| Login / Signup / Change password | `/login`, `/signup`, `/change-password` | `auth/*` | `guestGuard`, `sessionGuard` | `auth.spec.ts`, `auth-error-handler.spec.ts`, `auth-interceptor.spec.ts` | **BUILT** | — |
| Dashboard | `/dashboard` | `dashboard/dashboard.ts` | `authGuard` | `dashboard.spec.ts` | **PARTIAL** | Read-only. One GraphQL query (`productionUnits` + `cycles`); tiles and bars computed client-side. No drill-in, no action, and no FCR / cost / mortality — the backend has no such endpoint (§10) |
| Farms (admin) | `/farms` | `farms/farms.ts` | `permissionGuard(manage_farms)` | `farms.spec.ts` | **BUILT** | — |
| Approvals (admin) | `/approvals` | `approvals/approvals.ts` | `permissionGuard(approve_users)`; assign controls separately gated on `manage_users` | `approvals.spec.ts` | **BUILT** | — |
| Members (admin) | `/members` | `members/members.ts` | `permissionGuard(manage_users)` | `members.spec.ts` | **BUILT** | — |
| Production (units + cycles) | — | — | nav entries `navUnits`, `navCycles` exist but are **route-less placeholders**, rendered inert with a "coming soon" title (`app-shell.ts:45-46`, `app-shell.html:83`) | — | **MISSING** | Backend `createProductionUnit` / `createCycle` have no UI. `createProductionUnit` appears in `src/` exactly once — as a fixture string in `graphql.spec.ts` |
| Feed | — | — | `navFeeding` placeholder | — | **MISSING** | `logFeeding`, `recordFeedPurchase`, `feedStockBalance` all unreachable from the UI. `PERMISSION.LOG_FEEDING` / `MANAGE_FEED_STOCK` are declared in `core/models/permissions.ts` but gate nothing |
| Water quality | — | — | `navWater` placeholder | — | **MISSING** | `logWaterQuality` unreachable. `log_water_quality` is **not even declared** in `PERMISSION` — the only backend permission the frontend model omits |
| Tasks | — | — | **no nav entry at all** | — | **MISSING** | `dailyTasks` / `completeTask` unreachable. `PERMISSION.MARK_TASK_DONE` declared, gates nothing |
| Harvest | — | — | none | — | **MISSING** | No backend either (§5) |
| Reminders | — | — | none | — | **MISSING** | Backend is off-main (§9) |
| Finance | — | — | none | — | **MISSING** | No backend either (§10). `PERMISSION.VIEW_FINANCE` declared, gates nothing — mirrors the backend orphan |
| Reports | — | — | none | — | **MISSING** | No backend either |

Confirmed by keyword sweep over `src/`: `logFeeding`, `recordFeedPurchase`, `feedingLogs`,
`waterQuality`, `logWaterQuality`, `dailyTasks`, `completeTask`, `createCycle`,
`reminder`, `sale` — **zero hits each**.

> **Frontend: 4 / 13 screens BUILT, 1 PARTIAL, 8 MISSING.**
> Counted by the brief's ten module screens (admin as one): admin BUILT, Dashboard
> PARTIAL, and Feed / WQ / Tasks / Production / Harvest / Reminders / Finance / Reports
> all MISSING — **1 / 10 module screens BUILT.**

Frontend spec files: 12, covering auth, guards, the interceptor, the GraphQL transport,
the `has-permission` directive, the app-shell, and each of the four built screens.

---

## 14. Branches — what is and is not on `main`

**Backend**

| Branch | Merged into `main`? | Carries |
|---|---|---|
| `feat/auth-error-codes` | yes | ROOT password reset from environment |
| `feat/task-completions` | yes | task completions module |
| `feat/water-quality` | yes | water quality module |
| `fix/member-scoping-two-tier` | yes | two-tier scoping, `farms.name` UNIQUE |
| `fix/post-audit-backend-batch` | yes | post-audit fixes (D-3, D-7, D-8, D-12, D-13) |
| `test/integration-harness` | yes | the throwaway-Postgres harness |
| **`feat/reminders-scheduler`** | **NO — 4 commits ahead** | **The entire Reminders module: V12, entity, repositories, 4 services, scheduler, push sender, 621-line test. No API layer (by design).** |

**Frontend** — `feat/approvals-screen`, `feat/auth-reconcile`, `feat/farms-admin-screen`,
`feat/members-screen`, `fix/d4-graphql-error-handling`: **all merged, 0 commits ahead.**

---

## 15. The single sharpest gap

The backend can record everything a farm does day to day — feed, water, tasks — and none
of it is reachable from the UI. Three fully built, fully tested backend modules (Feed,
Water Quality, Daily Tasks) have **no screen**, and the fourth (Reminders) is not on
`main`. Meanwhile the entire commercial half of the SRS — harvest, costs, sales,
customers, reporting — is table-only on both sides.

---

### Prior reports, listed as artifacts only

`GAP_ANALYSIS.md`, `FIX_BATCH_REPORT.md`, `Data_Dictionary_Majedwali.md`,
`docs/TASK_COMPLETIONS_BATCH.md`, `docs/REMINDERS_BATCH.md` (backend);
`FRONTEND_BACKEND_AUDIT.md`, `docs/D4_AUDIT_REPORT.md`, `docs/FIX_D4_REPORT.md`,
`docs/FARMS_SCREEN_REPORT.md` (frontend). **None was used as evidence for any verdict
above.**
