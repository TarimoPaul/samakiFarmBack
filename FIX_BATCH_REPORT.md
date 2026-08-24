# Fix Batch Report — Backend (post-audit blockers + hygiene)

**Date:** 2026-08-23 — follow-up 2026-08-24 (F3 decision, D-13 follow-up, merge)
**Repo:** `D:\KAMPUNI PROJECT\spring-backend`
**Branch:** `fix/post-audit-backend-batch` (from `main` @ `8b030dc`)
**Verified against:** running instance on `http://localhost:8082`, PostgreSQL 17.5 `samakiFarm`
**Input:** `FRONTEND_BACKEND_AUDIT.md` (in the frontend repo)

Every fix compiles (`mvn -o clean compile`) and was committed separately. All evidence below is a real request against the running server, or SQL against the live database.

**Two defects not in the audit were found while verifying the acceptance criteria** — D-13 and D-14. Both are described in full below.

---

## Commits

| # | Commit | Fix |
|---|---|---|
| 1 | `42eb6ed` | `fix(security): centralize farm-scoping so modules cannot forget it (D-1)` — **F1** |
| 2 | `ace7656` | `feat(species): read-only species query, unblocking the create-cycle screen` — **F2** |
| 3 | `313ddfa` | `fix(errors): give GraphQL the same errorCode vocabulary as REST (D-2, D-6)` — **F3** |
| 4 | `b14e98b` | `feat(auth): GET /api/auth/me, exposing the caller's real permissions` — **F4** |
| 5 | `d197376` | `fix(scoping): say NO_FARM_CONTEXT instead of answering emptily (D-9)` |
| 6 | `2892ee3` | `fix(cycle): stop truncating fractional growth months (D-7)` |
| 7 | `672d2cb` | `fix(units): clean message for an invalid unit type (D-8)` |
| 8 | `3a983bd` | `fix(cycle): reject an unknown cycles(status:) instead of returning [] (D-12)` |
| 9 | `97d099c` | `chore: config and repo hygiene (D-5, D-10, D-11)` |
| 10 | `6408274` | `fix(rbac): PUT /api/roles/{id}/permissions was a guaranteed 500 (D-13, new)` |
| 11 | `056163a` | `fix(errors): unmapped paths answer 404, not 500 (D-14, new)` |
| 12 | `4b4c98e` | `fix(rbac): an unknown permission id now rejects the whole role edit (D-13 follow-up)` |

18 files, +585 / −39. **No Flyway migration was added or edited** — schema is still at V8, confirmed at startup (`Current version of schema "public": 8`). Nothing in F1–F4 or the hygiene sweep required a schema change, so `Data_Dictionary_Majedwali.md` and the ERD are **not** regenerated; they remain accurate.

### Test principals used throughout

| Principal | Identity | Farm | Role |
|---|---|---|---|
| ROOT | seeded root | *none* | `is_root` |
| **A** | `0788000111` "Fix Owner" | 15 (created for this batch) | OWNER |
| **B** | `0788000222` "Fix Worker" | 15 | WORKER |
| **C** | `0788000333` "Fix Norole" | 15 | *membership with no role* |

A/B/C were created through the real flow (`register` → `approve` → `memberships`). C exists because all four seeded roles hold `view_dashboard`, so a member with **no** role is the only way to test a read query being refused.

---

# PRIORITY FIXES

## F1 — D-1: farm-scoping centralized · **PASS**

### What changed

The check now lives in `PermissionChecker` as `requireResourceInCallersFarm(farmId)`, and both modules that resolve a client-supplied resource id call it. `CycleService.create` gained the check it never had; `FeedService.requireCycleInCallersFarm` was moved off `requireSameFarm` onto the same method, so there is one mechanism rather than one-per-module.

The two are deliberately **not** merged, and are documented against each other:

| Method | ROOT bypass | For |
|---|---|---|
| `requireSameFarm(farmId)` | **yes** | admin operations where farmId is part of the request — assigning memberships, changing roles, listing a farm's users. ROOT must cross farms here or the system cannot be bootstrapped. |
| `requireResourceInCallersFarm(farmId)` | **no** | production data. A caller with no farm has no farm to write into. |

### Audit of the other resolvers for the same gap

Checked every place a farm-scoped id or `farmId` arrives from the client:

| Call site | Status |
|---|---|
| `CycleService.create` (`unitId`) | **was the gap** — fixed |
| `FeedService.logFeeding` / `listFeedingLogs(cycleId)` | already checked ✔ (moved to the shared method) |
| `UserService.listByFarm(farmId)` | already calls `requireSameFarm` ✔ |
| `FarmUserService.assignMembership` / `changeRole` / `removeMembership` | already call `requireSameFarm` ✔ |
| `ProductionUnitService.create` / `listForCurrentFarm`, `CycleService.listForCurrentFarm`, `FeedService.recordPurchase` / `listPurchases` / `listStockMovements` / `feedStockBalance` | take farmId from the principal, never from input — not reachable ✔ |
| `FarmService.listAll` | returns all farms by design (`manage_farms` is the cross-farm admin capability) — unchanged |

**No other instance of the defect was found.** Nothing unrelated was changed.

`speciesId` is left unscoped, and that is now written down in the code: `species` is a shared system catalogue with no `farm_id` column at all.

### Evidence — the audit's attack, re-run

A is OWNER of farm **15**. Unit **1** belongs to farm **2**.

```
$ # token = A (farmId 15)
$ mutation { createCycle(input: {unitId: 1, speciesId: 2, stockingDate: "2026-08-23",
                                 fingerlingsCount: 10}) { cycleId unit { unitId } } }

{"errors":[{"message":"Huruhusiwi kufikia shamba hili.","path":["createCycle"],
 "extensions":{"errorCode":"FORBIDDEN","classification":"FORBIDDEN"}}],"data":null}
```

403 FORBIDDEN with the errorCode present ✔ (In the audit this same request returned `cycleId 3` inside farm 2.)

**No side effects** — farm 2 is untouched and only the legitimate cycle exists:

```sql
 unit_id | farm_id |  code  | status |              updated_by
---------+---------+--------+--------+--------------------------------------
       1 |       2 | T1     | IDLE   |                                        <- not flipped, not stamped
      24 |      15 | FIX-T1 | ACTIVE | 5dd7f8de-aeef-4638-aaf0-c6ab82afa4fe

 cycle_id | unit_id | species_id | expected_harvest_date | status
----------+---------+------------+-----------------------+--------
        4 |      24 |          1 | 2027-03-23            | ACTIVE   <- only the same-farm one

 task_id | cycle_id |     task_type
---------+----------+-------------------
      10 |        4 | Kulisha - Asubuhi                             <- 3 tasks, all for cycle 4
      11 |        4 | Kulisha - Jioni
      12 |        4 | Kuangalia Maji
```

### Evidence — same-farm creation still works

```
$ mutation { createCycle(input: {unitId: 24, speciesId: 1, stockingDate: "2026-08-23",
                                 fingerlingsCount: 500}) {…} }
{"data":{"createCycle":{"cycleId":"4","speciesName":"Sato","expectedHarvestDate":"2027-03-23",
 "status":"ACTIVE","unit":{"unitId":"24","code":"FIX-T1","status":"ACTIVE"}}}}
```

Harvest-date computation, unit flip to ACTIVE, and task generation all still behave ✔

---

## F2 — D-3: Species read API · **PASS**

`Query.species` returns `speciesId, name, growthMonthsAvg, avgHarvestWeightKg`, guarded by `view_dashboard`. Read-only; no create/update, as specified.

```
$ # A (OWNER)
$ query { species { speciesId name growthMonthsAvg avgHarvestWeightKg } }
{"data":{"species":[
  {"speciesId":"1","name":"Sato","growthMonthsAvg":7.0,"avgHarvestWeightKg":0.35},
  {"speciesId":"2","name":"Kambale","growthMonthsAvg":6.0,"avgHarvestWeightKg":1.0}]}}

$ # B (WORKER — also holds view_dashboard)
{"data":{"species":[{"speciesId":"1","name":"Sato",…},{"speciesId":"2","name":"Kambale",…}]}}

$ # C (member of farm 15 with no role → no view_dashboard)
{"errors":[{"message":"Huna ruhusa ya 'view_dashboard'.","path":["species"],
 "extensions":{"errorCode":"FORBIDDEN","classification":"FORBIDDEN"}}],"data":null}
```

Seeded rows returned for an authenticated user, FORBIDDEN without `view_dashboard` ✔ The create-cycle screen (frontend backlog item 9) is unblocked.

---

## F3 — D-2 + D-6: GraphQL error contract · **PASS**

One change: `ErrorCodes` gained `CONFLICT` and `VALIDATION_ERROR`, and `GraphQlExceptionResolver` gained a `DataIntegrityViolationException` branch plus a real code for `IllegalArgumentException`.

```
$ # duplicate (farm_id, code) — FIX-T1 already exists on farm 15
$ mutation { createProductionUnit(input: {code: "FIX-T1", type: "POND"}) { unitId } }
{"errors":[{"message":"Operesheni imekiuka vikwazo vya database (mfano: rudufu au uhusiano usiopo).",
 "path":["createProductionUnit"],
 "extensions":{"errorCode":"CONFLICT","classification":"BAD_REQUEST"}}],"data":null}
```
`errorCode = CONFLICT`, not `INTERNAL_ERROR for <uuid>` ✔

```
$ # negative quantityKg
$ mutation { recordFeedPurchase(input: {…, quantityKg: -5, unitCost: 100}) { purchaseId } }
{"errors":[{"message":"Thamani ya 'Kiasi cha chakula' lazima iwe zaidi ya sifuri.",
 "extensions":{"errorCode":"VALIDATION_ERROR","classification":"BAD_REQUEST"}}],"data":null}
```
errorCode present (was `null`), classification still `BAD_REQUEST` ✔

REST conflicts still answer 409:

```
$ curl -w "HTTP %{http_code}" -X POST /api/auth/register   # phone already registered
{"success":false,"message":"Namba ya simu hii tayari imesajiliwa."}
HTTP 409
```

> **Wider than the acceptance — confirmed and kept (2026-08-24).** The acceptance said "REST 409 for the same duplicate unchanged". Statuses are unchanged (400/409), but the matching **`errorCode` was also added to the REST envelope** (`CONFLICT` on 409, `VALIDATION_ERROR` on 400). This was flagged as wider than asked, with an offer to revert it; the decision is to **keep it**. The goal of F3 is that one failure looks the same through either API, and that only holds if both ends send the code. It is now load-bearing: the D-13 follow-up below relies on the REST `VALIDATION_ERROR` it added.
>
> One note on the duplicate specifically: `(farm_id, code)` has no REST endpoint at all, so the 409 shown above is a different conflict path (`ConflictException` from register). No REST route can trigger that exact constraint.

---

## F4 — Decision #4: `GET /api/auth/me` · **PASS**

Returns `id, name, phone, status, farmId, role` plus `permissions: string[]`. Permissions are **not** in the JWT — they come from the principal `JwtAuthFilter` fills from the database each request.

```
$ GET /api/auth/me   # A (OWNER)
{"success":true,"data":{"id":"5dd7f8de-…","name":"Fix Owner","phone":"0788000111",
 "status":"ACTIVE","farmId":15,"role":"OWNER","permissions":["approve_users","edit_cycle",
 "log_feeding","manage_farms","manage_feed_stock","manage_units","manage_users",
 "mark_task_done","view_dashboard","view_finance"]}}

$ GET /api/auth/me   # B (WORKER) — a different set
{… "farmId":15,"role":"WORKER","permissions":["log_feeding","mark_task_done","view_dashboard"]}}

$ GET /api/auth/me   # C (member, no role)
{… "farmId":15,"role":null,"permissions":[]}}

$ GET /api/auth/me   # ROOT — expanded to every permission, matching getRootAuthorities()
{… "farmId":null,"role":"ROOT","permissions":["approve_users",…,"view_finance"]}}

$ GET /api/auth/me   # no token
{"success":false,"message":"Hujaingia (login) - token haipo au si sahihi.",
 "errorCode":"UNAUTHENTICATED"}
HTTP 401
```

### A role edit is reflected without re-login

B's token was issued **once**, before the edit, and reused unchanged throughout:

```
$ GET /me (B)                      → permissions: ["log_feeding","mark_task_done","view_dashboard"]

$ PUT /api/roles/3/permissions  [1]        # ROOT; 1 = view_dashboard
  {"success":true,"data":{"roleId":3,"name":"WORKER","permissions":["view_dashboard"]}}

$ GET /me (B, SAME token)          → permissions: ["view_dashboard"]                    ✔

$ PUT /api/roles/3/permissions  [1,4,9]    # restore
  {"success":true,"data":{"roleId":3,"permissions":["mark_task_done","log_feeding","view_dashboard"]}}

$ GET /me (B, SAME token)          → permissions: ["log_feeding","mark_task_done","view_dashboard"]
```

WORKER's permissions were restored exactly (verified in SQL, see *Housekeeping*).

---

# HYGIENE SWEEP

## D-5 — `/actuator/health` · **PASS** (see also D-14)

The `permitAll` line is gone; actuator is still not a dependency, and the comment says to add the starter first if the endpoint is ever wanted.

```
$ GET /actuator/health              (no token)
{"success":false,"message":"Hujaingia (login) - token haipo au si sahihi.","errorCode":"UNAUTHENTICATED"}
HTTP 401

$ GET /actuator/health              (valid token)
{"success":false,"message":"Njia hii haipo."}
HTTP 404
```

It is no longer exposed as a health endpoint, and no longer 500s. The 404 half required D-14 below.

## D-7 — fractional growth months · **PASS**

Whole months are added as months; the remainder becomes days of the month it lands in.

Verified with a temporary species of **6.5** months (removed afterwards), stocked 2026-08-23:

```
$ query { species { … } }
… {"speciesId":"3","name":"FIXTEST Nusu","growthMonthsAvg":6.5,"avgHarvestWeightKg":0.5}

$ mutation { createCycle(input: {unitId: 26, speciesId: 3, stockingDate: "2026-08-23",
                                 fingerlingsCount: 100}) {…} }
{"data":{"createCycle":{"cycleId":"5","speciesName":"FIXTEST Nusu",
 "stockingDate":"2026-08-23","expectedHarvestDate":"2027-03-09"}}}
```

Check: `2026-08-23 + 6 months = 2027-02-23`; February 2027 has 28 days, `0.5 × 28 = 14`; `+14 days = 2027-03-09` ✔
The old `.longValue()` would have returned `2027-02-23` — two weeks early.

Whole-month species are unaffected: Sato (7.0) still gives `2027-03-23` (see F1).

## D-8 — invalid unit type · **PASS**

```
$ mutation { createProductionUnit(input: {code: "FIX-X9", type: "LAKE"}) { unitId } }
{"errors":[{"message":"Aina ya kitengo si sahihi. Chagua: TANK, POND, BWAWA.",
 "extensions":{"errorCode":"VALIDATION_ERROR","classification":"BAD_REQUEST"}}],"data":null}
```

Clean Swahili message with an errorCode; no Java class name. The list is generated from the enum, so it cannot drift.

## D-9 — ROOT / no farm context · **PASS**

```
$ query { cycles { … } }                          # ROOT
{"errors":[{"message":"ROOT hana shamba; tumia akaunti ya shamba husika.","path":["cycles"],
 "extensions":{"errorCode":"NO_FARM_CONTEXT","classification":"FORBIDDEN"}}],"data":null}

$ query { feedStockBalance }                      # ROOT — was a silent 0.0
{"errors":[{… "errorCode":"NO_FARM_CONTEXT","classification":"FORBIDDEN"}],"data":null}

$ mutation { createProductionUnit(…) }            # ROOT — was INTERNAL_ERROR for <uuid>
{"errors":[{… "errorCode":"NO_FARM_CONTEXT","classification":"FORBIDDEN"}],"data":null}
```

ROOT remains admin-only and its admin paths are unaffected:

```
$ GET /api/farms   (ROOT)   → HTTP 200, all 7 farms
```

A member with no farm gets a different message on the same code (`"Bado hujapangiwa shamba lolote…"`), since that is fixed by an admin assigning a membership, not by switching account.

## D-10 — stray directory · **PASS**

`src/main/java/com/samaki/farm/{config,domain,repository,rest,graphql,security,dto}` held 0 files and is deleted. It was never tracked by git (git does not track empty directories), so it produced no `git status` entry either before or after — confirmed on the filesystem instead:

```
$ ls src/main/java/com/samaki/farm/
auth/  common/  config/  cycle/  dailytask/  farm/  FarmBackendApplication.java
farmuser/  feed/  productionunit/  rbac/  species/  user/
```

## D-11 — GraphiQL / schema printer behind a profile · **PASS**

Defaulted **off** in `application.yml`; turned on only by the new `application-dev.yml`. Secure-by-default was chosen over the reverse: a deploy that forgets to set a profile gets the closed state, not the open one.

Both paths sit behind the auth chain, so they answer 401 without a token either way — the comparison below therefore uses a valid token:

| Path (with a valid token) | `dev` profile | `prod` profile |
|---|---|---|
| `/graphiql?path=/graphql` | **HTTP 200** | **HTTP 404** `{"message":"Njia hii haipo."}` |
| `/graphql/schema` | **HTTP 200** | **HTTP 404** `{"message":"Njia hii haipo."}` |
| `/api/auth/me` | HTTP 200 | HTTP 200 |

The API itself is unaffected under `prod` ✔

Local usage is documented in `.env.example`. The profile must come from the command line (`-Dspring-boot.run.profiles=dev`) or a real `SPRING_PROFILES_ACTIVE` env var — `spring.profiles.active` is not reliably read from a `spring.config.import` source, which is how `.env` is loaded here.

## D-12 — `cycles(status:)` validation · **PASS**

```
$ query { cycles(status: "ACITVE") { cycleId } }        # typo
{"errors":[{"message":"Hali ya mzunguko si sahihi. Chagua: ACTIVE, FAILED, HARVESTED.",
 "extensions":{"errorCode":"VALIDATION_ERROR","classification":"BAD_REQUEST"}}],"data":null}

$ query { cycles(status: "active") { cycleId status } }  # lowercase now normalised
{"data":{"cycles":[{"cycleId":"4","status":"ACTIVE"}]}}
```

---

# Defects found during this batch (not in the audit)

## D-13 — `PUT /api/roles/{id}/permissions` was a guaranteed 500 · **fixed** (`6408274`)

Found while verifying F4, whose acceptance requires editing a role and seeing `/me` change.

`RoleService` assigned `Set.copyOf(...)` to `Role.permissions`. That set is immutable, and Hibernate calls `clear()` on an entity collection during merge:

```
java.lang.UnsupportedOperationException: null
  at java.base/java.util.ImmutableCollections.uoe(ImmutableCollections.java:142)
  at java.base/java.util.ImmutableCollections$AbstractImmutableCollection.clear(…:149)
  at org.hibernate.type.CollectionType.replaceElements(CollectionType.java:512)
  at org.hibernate.event.internal.DefaultMergeEventListener.copyValues(…:582)
  …
  at org.springframework.data.jpa.repository.support.SimpleJpaRepository.save(…:632)
```

Every attempt to change a role's permissions answered `500 {"message":"Hitilafu ya ndani ya mfumo…"}` and wrote nothing. The endpoint has no UI and the audit listed it as `NO_UI` without exercising it, so the failure had never been seen. Both call sites now build a mutable `LinkedHashSet`. Evidence that it works is the F4 role-edit sequence above.

This matters beyond F4: **runtime-editable permissions are the premise of the whole RBAC design** — it is why `/me` exists rather than branching on role name — and the one endpoint that edits them could not run.

### Follow-up — unknown permission ids are now rejected outright (`4b4c98e`, 2026-08-24)

The first fix left one thing open: `permissionRepository.findAllById(ids)` silently drops ids that do not exist, so a request naming an unknown permission quietly assigned fewer permissions than asked and still answered `200`. **Decision: reject the whole edit.** A partial write is the worse failure here — this is the endpoint that writes security policy, and a silently-missing permission does not surface at the request that caused it, but later and somewhere else, as a user blocked from something they should have been allowed to do.

`RoleService.resolvePermissions()` now validates the entire list **before the role is touched**. Any unknown id — or a `null` inside the list — throws `IllegalArgumentException`, which `GlobalExceptionHandler` answers as `400` + `errorCode: VALIDATION_ERROR` (the REST code F3 added). Nothing is written, and no auth cache is cleared, because nothing changed. `createRole` takes the same path, so a role cannot be *created* with a quietly-trimmed permission set either.

Two things stay as they were, deliberately: an **empty list still clears every permission** (that is how a role is emptied), and **duplicate ids are still not an error** (asking for the same permission twice is the same request).

#### Evidence — the acceptance: an invalid id must leave the role untouched

WORKER is role 3. Baseline, then the request, then the same query again:

```sql
$ select r.role_id, r.name, string_agg(p.code||'#'||p.permission_id, ',' order by p.permission_id)
    from roles r left join role_permissions rp on rp.role_id=r.role_id
    left join permissions p on p.permission_id=rp.permission_id
   where r.role_id=3 group by r.role_id, r.name;

 role_id |  name  |                      perms                        -- BEFORE
---------+--------+-------------------------------------------------
       3 | WORKER | view_dashboard#1,mark_task_done#4,log_feeding#9
```
```
$ PUT /api/roles/3/permissions   [1,4,9,999]        # 999 does not exist
{"success":false,"message":"Ruhusa hizi hazipo: 999. Hakuna kilichobadilishwa.",
 "errorCode":"VALIDATION_ERROR"}
HTTP 400
```
```sql
 role_id |  name  |                      perms                        -- AFTER: identical
---------+--------+-------------------------------------------------
       3 | WORKER | view_dashboard#1,mark_task_done#4,log_feeding#9
```

`BAD_REQUEST` + `errorCode` ✓  role unchanged ✓

#### Evidence — the case the old code got wrong

`[1,999]` is a *shrinking* edit. The old code would have written `[view_dashboard]`, dropped two permissions, and answered `200`:

```
$ PUT /api/roles/3/permissions   [1,999]
{"success":false,"message":"Ruhusa hizi hazipo: 999. Hakuna kilichobadilishwa.",
 "errorCode":"VALIDATION_ERROR"}
HTTP 400

 role_id |  name  |                   perms                      -- still all three
---------+--------+-------------------------------------------
       3 | WORKER | view_dashboard,mark_task_done,log_feeding
```

#### Evidence — the rest of the surface

```
$ PUT /api/roles/3/permissions   [999,4,1000]      # several unknown, order kept
{"…":"Ruhusa hizi hazipo: 999, 1000. Hakuna kilichobadilishwa.","errorCode":"VALIDATION_ERROR"}  HTTP 400

$ PUT /api/roles/3/permissions   [1,null]
{"…":"Orodha ya ruhusa ina thamani tupu (null). Hakuna kilichobadilishwa.","errorCode":"VALIDATION_ERROR"}  HTTP 400

$ POST /api/roles  {"name":"BADROLE",…,"permissionIds":[1,999]}     # createRole, same rule
{"…":"Ruhusa hizi hazipo: 999. Hakuna kilichobadilishwa.","errorCode":"VALIDATION_ERROR"}  HTTP 400
   select count(*) from roles;  ->  4 before, 4 after — no role created
```

A valid edit still works, so D-13's own fix is not regressed:

```
$ PUT /api/roles/3/permissions   [1,4]
{"success":true,"data":{"roleId":3,"name":"WORKER","permissions":["mark_task_done","view_dashboard"]}}  HTTP 200

$ PUT /api/roles/3/permissions   [9,1,4,9,1]      # duplicates accepted, deduplicated
{"success":true,"data":{"roleId":3,"name":"WORKER",
 "permissions":["mark_task_done","log_feeding","view_dashboard"]}}  HTTP 200
```

WORKER was restored to its baseline by that last call — `role_permissions` is back to 21 rows and all four roles hold exactly what they held before this batch.

## D-14 — unmapped paths answered 500, not 404 · **fixed** (`056163a`)

Surfaced by D-5. `NoResourceFoundException` fell through to the catch-all `Exception` handler, so **any** mistyped URL returned `500 "Hitilafu ya ndani ya mfumo"` — telling the caller the server is broken when they had asked for something that does not exist, and logging a false ERROR each time.

Removing the `/actuator/health` permit line fixed the anonymous case (401) but left authenticated callers getting 500 on that same path, so D-5 was only half-done without this. Now:

```
$ GET /actuator/health  (valid token)  → HTTP 404 {"success":false,"message":"Njia hii haipo."}
```

---

# Housekeeping — test data created and removed

All created through the real API (except the temporary species, which has no create API by design) and removed afterwards.

| Created | Removed |
|---|---|
| Farm 15 "FIXBATCH Shamba" | ✅ deleted |
| Users `5dd7f8de…` (A), `cf2e9627…` (B), `48975dd9…` (C) | ✅ deleted |
| Their 3 `farm_users` memberships on farm 15 | ✅ deleted |
| Production units 24 `FIX-T1`, 26 `FIX-T2` | ✅ deleted |
| Cycles 4 and 5 | ✅ deleted |
| Daily tasks 10–15 (3 per cycle) | ✅ deleted |
| Species 3 "FIXTEST Nusu" (6.5 months, inserted by SQL for D-7) | ✅ deleted |
| WORKER role permissions temporarily reduced to `view_dashboard` for F4 | ✅ restored via the same API |
| WORKER role permissions reduced again on 2026-08-24, verifying the D-13 follow-up | ✅ restored via the same API (`role_permissions` back to 21) |

**No cross-farm collateral this time** — that was the point of F1. Farm 2's unit 1 was verified untouched (`IDLE`, `updated_by` NULL) both immediately after the blocked attack and after cleanup.

Post-cleanup verification, against the baseline captured before the batch:

```sql
  c |        t                      -- all identical to the pre-batch baseline
----+------------------
  7 | users
  6 | farms
  6 | farm_users
 20 | production_units
  0 | cycles
  0 | daily_tasks
  2 | species
 21 | role_permissions

  name  |                string_agg                 -- WORKER restored exactly
--------+-------------------------------------------
 WORKER | log_feeding,mark_task_done,view_dashboard

 unit_id | farm_id | code | status |          updated_at           | updated_by
---------+---------+------+--------+-------------------------------+------------
       1 |       2 | T1   | IDLE   | 2026-08-21 11:27:45.060424+03 |
```

Not reverted (harmless, not removable): sequence values for `farms`, `production_units`, `cycles`, `daily_tasks`, `species` advanced past the deleted rows.

**Environment left as found:** the backend is running again on `:8082` under the `dev` profile, so GraphiQL is available exactly as it was before this batch. Note that it now needs that profile explicitly — `mvn -o spring-boot:run -Dspring-boot.run.profiles=dev`.

---

# Open items

1. ~~**F3's REST-side errorCode**~~ — **closed 2026-08-24: kept.** REST keeps sending `CONFLICT` / `VALIDATION_ERROR`, so one failure looks the same through either API.
2. ~~**D-13 follow-up**~~ — **closed 2026-08-24 (`4b4c98e`): reject.** An unknown permission id fails the whole edit with `400 VALIDATION_ERROR` and leaves the role untouched. Evidence under D-13 above.
3. **Nothing else from the audit was touched.** Still open and out of scope for this batch, as instructed: D-4 (frontend GraphQL error handling — the next, frontend batch), Daily Tasks read/complete/assign + reminders scheduler, correction/reversal mutations, multi-farm switching, and the absent modules (Water Quality, Finance, Assets).
4. **Merged 2026-08-24.** `fix/post-audit-backend-batch` (14 commits) was merged into `main`. Nothing is pushed — `main` is now 23 commits ahead of `origin/main`.
