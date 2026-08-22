# GAP_ANALYSIS.md — Backend Audit & Reconciliation (Phase 1)

**Repo:** `D:\KAMPUNI PROJECT\spring-backend`
**Audited:** 2026-08-22
**Scope:** Phase 1 only — audit and reconcile. No application code or migrations were created, modified, or deleted.

---

## 0. Preconditions that invalidate parts of the brief

Four assumptions in the audit brief do not hold against this repository. They are stated first because they change how the rest of this document must be read.

### 0.1 The three reference documents do not exist

`Mahitaji_ya_Mfumo_SRS.md`, `ERD_Muundo_wa_Database.mermaid`, and `Data_Dictionary_Majedwali.md` are **not present anywhere** under `D:\KAMPUNI PROJECT` (searched recursively, excluding `node_modules`/`dist`/`target`). The only Markdown files in the tree are `spring-backend/README.md` and `samakiFarmFront/README.md`. `D:\KAMPUNI PROJECT` is not a Git repository, so there is no history to recover deleted copies from.

**Consequence:** a true three-way reconciliation is impossible. This audit performs a **two-way** reconciliation — code/migrations versus the 8-group / 20-entity ERD target **quoted inline in the brief**, which is the only surviving statement of the target. Every "In Data Dictionary?" cell is `UNKNOWN (doc missing)`.

### 0.2 There is no self-registration + admin-approval flow

The brief states an approval flow "was added to the code that appears in NO document." **No such flow exists in this codebase.**

- Grep for `PENDING|APPROV|approve|pending|INACTIVE|SUSPEND` (case-insensitive) across `src/` returns **two hits, both unrelated**: `task_completions.status DEFAULT 'PENDING'` and `reminders.status DEFAULT 'PENDING'` in `V1__init_schema.sql`.
- There is **self-registration** (`POST /api/auth/signup`), but it is immediate and unmoderated: it creates the farm, creates the owner, and returns a working JWT in the same request. Nothing gates it.

Per the source-of-truth rule, no approval logic was invented and none was removed. Task 4 below reports the login contract that actually exists, plus the closest real analogue to the unapproved case.

### 0.3 Migration naming differs

The brief refers to `001_init_schema.sql`. Actual files use Flyway's `V<n>__` convention: `V1__init_schema.sql` … `V4__merge_users_into_farm_users.sql`.

### 0.4 The schema changed during this session, by explicit human decision

`V4__merge_users_into_farm_users.sql` was written and applied **in this working session** at the human's instruction. It merges the ERD's `users` and `farm_users` into a single `farm_users` table. This is a deliberate, approved divergence from the ERD target — not drift. It is reported as a conflict in §2 because the ERD must now be updated to match, and it is listed under "Decisions needed" for confirmation.

---

## Task 1 — Inventory of the actual backend

### 1.1 Flyway migrations

Location: `src/main/resources/db/migration/`. All four are applied and successful (`flyway_schema_history`: V1–V3 on 2026-08-20, V4 on 2026-08-22 11:58:51, `success = t` for all).

#### `V1__init_schema.sql` — creates **20 tables**

| # | Table | Columns | Key constraints |
|---|---|---|---|
| 1 | `roles` | role_id, name, description | PK role_id (SERIAL); UNIQUE name |
| 2 | `permissions` | permission_id, code, module, group_name, description | PK permission_id; UNIQUE code |
| 3 | `role_permissions` | role_id, permission_id | PK (role_id, permission_id); FK→roles, FK→permissions, both ON DELETE CASCADE |
| 4 | `users` | user_id, name, phone, email, password_hash, push_token, status, is_root, created_at | PK user_id (UUID); UNIQUE phone; UNIQUE email; status DEFAULT 'ACTIVE'; is_root DEFAULT false |
| 5 | `farms` | farm_id, name, location, owner_user_id, created_at | PK farm_id; FK owner_user_id→users |
| 6 | `farm_users` | farm_id, user_id, role_id | PK (farm_id, user_id); FK→farms CASCADE, FK→users CASCADE, FK→roles |
| 7 | `assets` | asset_id, farm_id, name, category, purchase_date, value, status | PK asset_id; FK farm_id→farms CASCADE |
| 8 | `production_units` | unit_id, farm_id, code, type, size_m3, water_source, status | PK unit_id; FK farm_id→farms CASCADE; CHECK type IN (TANK,POND,BWAWA); UNIQUE (farm_id, code) |
| 9 | `species` | species_id, name, growth_months_avg, avg_harvest_weight_kg | PK species_id; UNIQUE name |
| 10 | `cycles` | cycle_id, unit_id, species_id, stocking_date, fingerlings_count, survival_rate_estimate, expected_harvest_date, actual_harvest_date, status | PK cycle_id; FK unit_id→production_units CASCADE; FK species_id→species |
| 11 | `feed_purchases` | purchase_id, farm_id, purchase_date, feed_type, quantity_kg, unit_cost, total_cost (GENERATED), supplier | PK purchase_id; FK farm_id→farms CASCADE |
| 12 | `feeding_logs` | log_id, cycle_id, log_date, feed_type, quantity_kg, recorded_by_user_id | PK log_id; FK cycle_id→cycles CASCADE; FK recorded_by_user_id→users |
| 13 | `feed_stock_movements` | movement_id, farm_id, direction, quantity_kg, reference_purchase_id, reference_feeding_log_id, moved_at | PK movement_id; CHECK direction IN (IN,OUT); FKs→farms/feed_purchases/feeding_logs |
| 14 | `water_quality_logs` | log_id, unit_id, log_date, ph, temperature, oxygen, notes, recorded_by_user_id | PK log_id; FK unit_id→production_units CASCADE; FK recorded_by_user_id→users |
| 15 | `daily_tasks` | task_id, cycle_id, task_type, scheduled_time, frequency, assigned_role_id | PK task_id; FK cycle_id→cycles CASCADE; FK assigned_role_id→roles |
| 16 | `task_completions` | completion_id, task_id, completion_date, completed_by_user_id, completed_at, status, notes | PK completion_id; FK task_id→daily_tasks CASCADE; FK completed_by_user_id→users; UNIQUE (task_id, completion_date); status DEFAULT 'PENDING' |
| 17 | `reminders` | reminder_id, task_id, channel, send_time, status | PK reminder_id; FK task_id→daily_tasks CASCADE; CHECK channel IN (PUSH,SMS); status DEFAULT 'PENDING' |
| 18 | `costs` | cost_id, cycle_id, category, description, amount, cost_date | PK cost_id; FK cycle_id→cycles CASCADE |
| 19 | `customers` | customer_id, name, phone, type | PK customer_id |
| 20 | `sales` | sale_id, cycle_id, customer_id, sale_date, kg, price_per_kg, total_amount (GENERATED) | PK sale_id; FK cycle_id→cycles CASCADE; FK customer_id→customers |

Also creates 7 indexes and seeds 4 roles (OWNER, FARM_MANAGER, WORKER, VIEWER) and 2 species (Sato, Kambale). Permissions are **not** seeded here — they are loaded at runtime from CSV (see §1.5).

#### `V2__add_audit_and_soft_delete_columns.sql` — creates 0 tables

Adds audit/soft-delete columns to **8 tables** (`users`, `farms`, `roles`, `permissions`, `cycles`, `daily_tasks`, `production_units`, `species`): `updated_at`, `deleted_at`, `is_deleted`, `updated_by`, `deleted_by`; plus `created_at` for the six that lacked it.

**Not covered:** `assets`, `feed_purchases`, `feeding_logs`, `feed_stock_movements`, `water_quality_logs`, `task_completions`, `reminders`, `costs`, `customers`, `sales`, `role_permissions`, `farm_users`. Any future entity for those tables cannot extend `BaseEntity` without a further migration.

#### `V3__add_password_reset_otps.sql` — creates **1 table**

`password_reset_otps` (otp_id BIGSERIAL PK, user_id FK→users CASCADE, code_hash, expires_at, attempts, used_at + full audit column set) and index `idx_password_reset_otps_user_created`.

#### `V4__merge_users_into_farm_users.sql` — creates 0, drops 1, renames 1

Adds `farm_id`/`role_id` (both NULLable) to `users`; copies membership from the `farm_users` join table via `DISTINCT ON (user_id) … ORDER BY user_id, farm_id`; `DROP TABLE farm_users`; `ALTER TABLE users RENAME TO farm_users`; renames 3 constraints; creates `idx_farm_users_farm`.

**Verified in the live database:** `users` no longer exists; `farm_users` has all 16 merged columns; **23 foreign keys** now reference `farm_users` and all survived the rename (PostgreSQL binds FKs by OID, not name).

### 1.2 JPA entities → tables

| Entity class | Package | Table | Notes |
|---|---|---|---|
| `BaseEntity` | `common.entity` | *(none)* | `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener)` |
| `FarmUser` | `farmuser.entity` | `farm_users` | UUID PK; carries identity **and** `farm`/`role` since V4 |
| `Farm` | `farm.entity` | `farms` | |
| `Role` | `rbac.entity` | `roles` | `@ManyToMany` → `role_permissions` join table (no entity) |
| `Permission` | `rbac.entity` | `permissions` | |
| `ProductionUnit` | `productionunit.entity` | `production_units` | enum `UnitType {TANK, POND, BWAWA}` |
| `Species` | `species.entity` | `species` | |
| `Cycle` | `cycle.entity` | `cycles` | |
| `DailyTask` | `dailytask.entity` | `daily_tasks` | |
| `PasswordResetOtp` | `auth.entity` | `password_reset_otps` | |

**9 entities mapping to 9 tables.** All 9 carry `@SQLRestriction("is_deleted = false")`.

**11 tables have no JPA entity:** `assets`, `feed_purchases`, `feeding_logs`, `feed_stock_movements`, `water_quality_logs`, `task_completions`, `reminders`, `costs`, `customers`, `sales`, and `role_permissions` (the last is intentional — mapped as a `@JoinTable`).

Repositories exist 1:1 for the 9 entities, plus none for the unmapped tables.

### 1.3 REST controllers

| Controller | Method + path | Authorization |
|---|---|---|
| `AuthController` (`auth.controller`) | `POST /api/auth/signup` | **permitAll** |
| | `POST /api/auth/login` | **permitAll** |
| | `POST /api/auth/forgot-password` | **permitAll** |
| | `POST /api/auth/reset-password` | **permitAll** |
| `UserController` (`farmuser.controller`) | `POST /api/users` | `@PreAuthorize("hasAuthority('manage_users')")` + `requireSameFarm(req.farmId)` in service |
| | `GET /api/users?farmId=` | `manage_users` + `requireSameFarm` |
| | `PUT /api/users/{userId}/role` | `manage_users` + same-farm + not-ROOT |
| | `DELETE /api/users/{userId}` | `manage_users` + same-farm + not-ROOT + not-self + not-farm-owner |
| `RoleController` (`rbac.controller`) | `GET /api/roles` | `manage_users` |
| | `POST /api/roles` | `manage_users` |
| | `PUT /api/roles/{roleId}/permissions` | `manage_users` |
| | `GET /api/roles/permissions` (paginated) | `manage_users` |

All business logic lives in `*/services/` (`AuthService`, `FarmUserService`, `RoleService`, `RbacSeedService`, `PasswordResetService`); controllers are HTTP-only and return the `ApiResponse<T>` envelope. Errors are raised as exceptions and mapped centrally by `common.web.GlobalExceptionHandler`: `ConflictException`→409, `UnauthorizedException`→401, `IllegalArgumentException`→400, `AccessDeniedException`→403, `DataIntegrityViolationException`→409, `Exception`→500.

### 1.4 GraphQL

Schema: `src/main/resources/graphql/schema.graphqls`. Endpoint `POST /graphql`; GraphiQL enabled (`spring.graphql.graphiql.enabled: true` — **must be disabled for production**).

| Kind | Name | Resolver | Permission | Module |
|---|---|---|---|---|
| Query | `productionUnits: [ProductionUnit!]!` | `ProductionUnitResolver` | `view_dashboard` | Production Units |
| Query | `cycles(status: String): [Cycle!]!` | `CycleResolver` | `view_dashboard` | Cycles |
| Mutation | `createProductionUnit(input): ProductionUnit!` | `ProductionUnitResolver` | `manage_units` | Production Units |
| Mutation | `createCycle(input): Cycle!` | `CycleResolver` | `edit_cycle` | Cycles |
| Field | `Cycle.speciesName: String!` | `CycleResolver.speciesName` (`@SchemaMapping`) | — | Cycles |
| Type | `ProductionUnit`, `Cycle` | | | |
| Input | `CreateProductionUnitInput`, `CreateCycleInput` | | | |

Startup schema inspection reports `Unmapped fields: {}`, `Unmapped registrations: {}`, `Unmapped arguments: {}` — the schema and resolvers are fully consistent.

Note the schema comment: *"Moduli zilizobaki (Feed, WaterQuality, DailyTasks, Finance) zitaongezwa hapa kwa muundo uleule"* — the absent modules are acknowledged in-schema.

### 1.5 Security / JWT configuration

**Token** (`auth.security.JwtUtil`) — HMAC-SHA, key from `app.jwt.secret`, **expiry 12 hours** (`app.jwt.expiration-hours`). Claims:

| Claim | Value | Used for |
|---|---|---|
| `sub` | userId (UUID) | identity — the only claim trusted for authorization |
| `isRoot` | boolean | ROOT bypass |
| `farmId`, `roleId`, `roleName` | context | UI display only |

**The token deliberately does not carry permissions.** `JwtAuthFilter` reads role and permissions **fresh from the database on every request**, so permission changes take effect without re-login.

**Caching** (static, in-process, in `JwtAuthFilter`): regular users 15 min (`ConcurrentHashMap`), ROOT authorities 5 min. Invalidated explicitly by `clearUserCache(userId)` (role change, delete), `clearAllUserCache()` and `clearRootCache()` (role-permission edits, seeding).

**Filter chain** (`config.SecurityConfig`): CSRF disabled, CORS from `app.cors.allowed-origins` (default `http://localhost:4200`), `SessionCreationPolicy.STATELESS`, `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`. `permitAll` on `/api/auth/**` and `/actuator/health`; everything else authenticated. `/graphql` is *authenticated* at the chain level, with per-operation RBAC inside resolvers via `PermissionChecker`.

> **Defect (minor):** `/actuator/health` is permitted but `spring-boot-starter-actuator` is **not a dependency**, so the endpoint does not exist.

**RBAC seeding** (`rbac.services.RbacSeedService`, run by `rbac.config.RbacDataInitializer`): permissions loaded idempotently from `seed/permissions.csv`; role→permission mapping from `seed/role_permissions.csv` **once per role that has no permissions yet**; ROOT user created from `app.root.*` properties with `is_root = true` and no farm/role.

**Seeded permissions (6):** `view_dashboard`, `edit_cycle`, `manage_units`, `mark_task_done` (FARM); `view_finance` (FINANCE); `manage_users` (UAA).

> **Finding:** `mark_task_done` and `view_finance` are seeded and assigned to roles but are **never checked anywhere in the codebase** — they are dead permissions, because the Task Completions and Finance modules do not exist. Grep confirms zero enforcement sites for either code.

**Auditing** (`config.AuditingConfig`): `@EnableJpaAuditing` with an `AuditorAware<UUID>` reading `AuthenticatedUser` from the `SecurityContext`; populates `BaseEntity.updatedBy`. There is no `created_by` column, so `@CreatedBy` is not used.

### 1.6 Module classification

| # | Module | Status | Evidence |
|---|---|---|---|
| 1 | **RBAC** | **IMPLEMENTED** | `rbac/` (Role, Permission, repos, RoleService, RoleController, RbacSeedService), `auth/security/` (JwtAuthFilter, PermissionChecker, CustomPermissionEvaluator), `farmuser/` (full CRUD-ish: create/list/update-role/delete), `seed/*.csv` |
| 2 | **Farms / Scope** | **PARTIAL** | `Farm` entity + `FarmRepository` exist and scoping works everywhere (`requireSameFarm`), but there is **no Farm controller or resolver**. A farm can only ever be created as a side effect of `POST /api/auth/signup`; it can never be listed, renamed, or relocated through the API. |
| 3 | **Assets & Production Units** | **PARTIAL** | ProductionUnit: IMPLEMENTED (entity, repo, service, resolver, 1 query + 1 mutation). **`assets`: ABSENT** — table exists in V1, no entity/repo/API at all. |
| 4 | **Species & Cycles** | **PARTIAL** | Cycle: IMPLEMENTED, and the richest logic in the system (`CycleService.create` computes `expected_harvest_date` from `species.growth_months_avg` per FR-3.2 and auto-generates 3 `daily_tasks` per FR-4.1). **Species: read-only and unexposed** — entity + repo exist, seeded with 2 rows in V1, but no query/mutation; reachable only through `Cycle.speciesName`. |
| 5 | **Feed** | **ABSENT** | `feed_purchases`, `feeding_logs`, `feed_stock_movements` exist as tables only. No entity, repository, service, resolver, or schema type. |
| 6 | **Water Quality** | **ABSENT** | `water_quality_logs` exists as a table only. Nothing else. |
| 7 | **Daily Tasks & Reminders** | **PARTIAL** | `DailyTask` entity + repo exist and rows are auto-created by `CycleService`. But there is **no API to read, edit, or assign tasks**; `task_completions` and `reminders` are **ABSENT** (tables only); there is **no scheduler** (no `@Scheduled`, no `@EnableScheduling`); `SmsSender` has only the `LoggingSmsSender` stub, which logs instead of sending. |
| 8 | **Finance** | **ABSENT** | `costs`, `sales`, `customers` exist as tables only. Nothing else. |

**Summary: 1 of 8 fully implemented, 3 partial, 4 absent.**

---

## Task 2 — Reconciliation (Code vs ERD target vs Data Dictionary)

Reconciled against the 8-group / 20-entity ERD target quoted in the brief. The Data Dictionary column is unresolvable (§0.1).

| Table | In migrations? | JPA entity? | In ERD? | In Data Dict.? | Verdict |
|---|---|---|---|---|---|
| `users` | **No** (renamed away by V4) | No (class deleted) | Yes | UNKNOWN | **FIELD_MISMATCH** — merged into `farm_users` by human decision this session |
| `roles` | Yes (V1) | Yes (`Role`) | Yes | UNKNOWN | **OK** |
| `permissions` | Yes (V1) | Yes (`Permission`) | Yes | UNKNOWN | **FIELD_MISMATCH** — code adds `module`, `group_name` beyond a bare code/description |
| `role_permissions` | Yes (V1) | No (`@JoinTable`) | Yes | UNKNOWN | **OK** — join table, entity intentionally absent |
| `farms` | Yes (V1) | Yes (`Farm`) | Yes | UNKNOWN | **OK** |
| `farm_users` | Yes (V1, replaced by V4) | Yes (`FarmUser`) | Yes | UNKNOWN | **FIELD_MISMATCH** — no longer a 3-column join table; now the merged user table (16 columns) |
| `production_units` | Yes (V1) | Yes | Yes | UNKNOWN | **OK** |
| `assets` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** (table only, no entity/API) |
| `species` | Yes (V1) | Yes | Yes | UNKNOWN | **OK** (entity present; not exposed via API) |
| `cycles` | Yes (V1) | Yes | Yes | UNKNOWN | **OK** |
| `feeding_logs` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `feed_purchases` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `feed_stock_movements` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `water_quality_logs` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `daily_tasks` | Yes (V1) | Yes | Yes | UNKNOWN | **OK** |
| `task_completions` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `reminders` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `costs` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `sales` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `customers` | Yes (V1) | **No** | Yes | UNKNOWN | **MISSING_IN_CODE** |
| `password_reset_otps` | Yes (V3) | Yes | **No** | UNKNOWN | **UNDOCUMENTED** |

**Totals:** OK 7 · FIELD_MISMATCH 3 · MISSING_IN_CODE 10 · UNDOCUMENTED 1.

Note that all 10 `MISSING_IN_CODE` rows are "missing" only at the **application layer** — every one of those tables physically exists in the database from V1. The gap is entities/services/API, not schema.

### 2.1 Resolving the 17-vs-20 table count

**Actual counts, verified against the live database:**

| Measure | Count |
|---|---|
| Tables created by `V1` | 20 |
| Added by `V3` (`password_reset_otps`) | +1 → 21 |
| Net change from `V4` (drop join table, rename `users`) | −1 → **20** |
| Application tables in the database now | **20** |
| Plus `flyway_schema_history` | 21 total |

So the migrations produce **exactly 20 application tables**, matching the ERD's count — but **not the same 20**. The composition differs:

- ERD has `users` and `farm_users` as two tables; code now has one (`farm_users`).
- Code has `password_reset_otps`, which the ERD does not list.

These two differences cancel out numerically, which is a coincidence and should not be read as agreement.

**On the Data Dictionary's claim of 17:** this cannot be verified or attributed — the document does not exist (§0.1). The gap of 3 cannot be assigned to specific tables without it. The most likely candidates, based on what documentation of this kind usually omits, are the pure join/derived tables (`role_permissions`, `farm_users`, `feed_stock_movements`), but **this is a hypothesis, not a finding.** Recovering the file, or accepting that the Data Dictionary must be regenerated from the schema, is a decision for the human.

### 2.2 Resolving `users.status`

**What the code actually does:**

| Aspect | Finding |
|---|---|
| Column | `farm_users.status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` (created in V1 on `users`, carried through the V4 rename) |
| Entity field | `FarmUser.status`, initialised to `"ACTIVE"` (`farmuser/entity/FarmUser.java:62`) |
| Values written | **Only `'ACTIVE'`** — `RbacSeedService:190` for ROOT; the field default for everyone else. No other value is ever assigned. |
| Values read | **None.** Grep for `getStatus()` across `src/main/java` finds no read of `FarmUser.status` anywhere. The other `setStatus` hits are on `ProductionUnit` and `Cycle`, which are different fields. |
| Distinct values in the live DB | `ACTIVE` (single row in the result set) |
| `PENDING_APPROVAL` or equivalent | **Does not exist** — not in the schema, not in the entity, not anywhere in the codebase |

**Conclusion:** `farm_users.status` is **dead metadata**. It is written once and never consulted; it gates nothing. There is no `INACTIVE` state in practice, and no approval state at all. The Data Dictionary's `ACTIVE/INACTIVE` claim is therefore half-right in intent and wholly unimplemented in behaviour.

Deactivation *is* implemented in this system, but through a different mechanism entirely: the `is_deleted` soft-delete flag plus `@SQLRestriction("is_deleted = false")`, driven by `DELETE /api/users/{userId}`. This means **two overlapping deactivation concepts exist in the schema**, one live and one dead — see "Decisions needed".

---

## Task 3 — Build & run status

### Compile

```
mvn -o clean compile
→ BUILD SUCCESS (61 source files, javac release 17)
→ exit code 0
```

**It compiles.** Note that `README.md` §"Kikwazo cha Ukaguzi" still claims the project has never been compiled and may not build — that statement is now **stale** and should be corrected.

There is **no Maven wrapper** (`mvnw`/`mvnw.cmd` are absent) despite the README implying local builds; `mvn` must be on `PATH`.

### Run

`mvn -o spring-boot:run` **failed to bind**, with this verbatim error:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Web server failed to start. Port 8082 was already in use.

Action:

Identify and stop the process that's listening on port 8082 or configure this application to listen on another port.
```

**This is not a defect.** Port 8082 was already held by an instance the human had started; that instance responded correctly to live HTTP probes throughout this audit. Crucially, the failed run still proves the parts that matter, because the failure occurs *after* persistence initialisation:

- Flyway ran and found the schema current.
- `LocalContainerEntityManagerFactoryBean` was **built successfully**, which is where `spring.jpa.hibernate.ddl-auto: validate` executes. **Hibernate schema validation passes** against all 9 entities — including the post-V4 `FarmUser` mapping. A mismatch would have thrown `SchemaManagementException` here instead.
- GraphQL schema inspection reported no unmapped fields, registrations, or arguments.

Only `webServerStartStop` failed. No other errors or warnings of substance appeared in the startup log.

**Live database state verified** (PostgreSQL 17, `samakiFarm`): V1–V4 all `success = t`; `users` absent; `farm_users` present with all 16 merged columns; 23 FKs correctly retargeted; `idx_farm_users_farm` present; constraints renamed to `farm_users_pkey` / `_phone_key` / `_email_key`; 7 user rows, all with `farm_id`+`role_id` populated except ROOT (NULL/NULL, as designed); RBAC intact (4 roles, 6 permissions, 14 role-permission links).

---

## Task 4 — Login contract check

`POST /api/auth/login` → `AuthController.login` → `AuthService.login` → `GlobalExceptionHandler`.

Probed live against the running instance on port 8082:

| Case | HTTP | Body | Distinguishable? |
|---|---|---|---|
| **(a) Valid credentials** | `200` | `{"success":true,"data":{"token":"<JWT>","user":{"id":"…","name":"…","role":"OWNER"}}}` | — |
| **(b) Wrong password** | `401` | `{"success":false,"message":"Simu/barua pepe au password si sahihi."}` | baseline |
| **(b′) Neither phone nor email supplied** | `400` | `{"success":false,"message":"Namba ya simu au barua pepe inahitajika."}` | yes |
| **(c) Unapproved user** | **N/A** | **The state does not exist** — there is no approval flow (§0.2) | — |

### The closest real analogue to case (c), and the defect it reveals

Three states exist in which a *real, correctly-authenticating* user is refused. Their responses are **not** uniformly distinguishable:

| State | HTTP | Message | Distinguishable from wrong password? |
|---|---|---|---|
| Soft-deleted (`is_deleted = true`) | `401` | `"Simu/barua pepe au password si sahihi."` | **NO — byte-identical** |
| No farm/role assigned | `403` | `"Mtumiaji huyu hajaunganishwa na shamba lolote."` | Yes |
| `status = 'INACTIVE'` | `200` | logs in normally | **NO — not checked at all** |

**Verified empirically.** Logging in as a soft-deleted user with the *correct* password returns exactly the same status and body as the same user with a *wrong* password:

```
(c) deleted user, CORRECT password  → HTTP 401  {"success":false,"message":"Simu/barua pepe au password si sahihi."}
(b) deleted user, WRONG   password  → HTTP 401  {"success":false,"message":"Simu/barua pepe au password si sahihi."}
```

Mechanism: `@SQLRestriction("is_deleted = false")` on `FarmUser` filters the derived query `findByPhone`, so `AuthService.login` sees `Optional.empty()` and cannot tell "deactivated" from "no such user".

> ### DEFECT — deactivated users are indistinguishable from wrong credentials
>
> A user removed via `DELETE /api/users/{userId}` receives the generic "wrong phone/password" error. They will reasonably conclude they mistyped, and will retry, contact support, or attempt a password reset — none of which can succeed. This is exactly the failure mode the brief anticipated for unapproved users; it exists here for deactivated ones.
>
> **Aggravating factor:** the fix is not simply "return 403 for deleted users." Returning a distinguishable response would leak account existence, which is precisely what `POST /api/auth/forgot-password` deliberately protects against (it returns a generic message *by design*, documented in `AuthService.requestPasswordReset`, to prevent user enumeration). Login and password-reset would then disagree about whether account existence is a secret. **This is a policy decision, not a code fix** — see "Decisions needed".
>
> **Secondary defect:** `status = 'INACTIVE'` is never checked, so if that column were ever used as intended it would grant, not deny, access. Any status-based gate would have to be built from scratch.

### Additional login-contract observations

- `ApiResponse` carries an `errorCode` field, but **no code path ever populates it**. Every error is a human-readable Swahili string only. A client cannot branch on failure reason programmatically — relevant if the frontend must distinguish these cases.
- `AuthService.login` performs **no rate limiting and no lockout**. Password-reset OTP has a 60-second resend cooldown and a 5-attempt cap (`PasswordResetService`), but login itself is unthrottled and therefore brute-forceable.
- Login succeeds via phone **or** email; `LoginRequest` requires only one.
- ROOT logs in with no farm/role and receives a token with `isRoot: true`.

---

## Decisions needed from the human

Ordered by blocking impact on Phase 2.

1. **The three reference documents are missing.** `SRS`, `ERD`, `Data_Dictionary` are not in the repo or anywhere under `D:\KAMPUNI PROJECT`, and there is no Git history to recover them from. Phase 2 instructs updating these files — that is impossible as written. **Decide:** recover them from wherever they actually live and re-run this reconciliation, or accept that they must be regenerated from the final schema as a Phase 2 deliverable.

2. **The brief's approval-flow premise is false.** No self-registration approval logic exists (§0.2). Signup is immediate and unmoderated: anyone who can reach `POST /api/auth/signup` creates a farm and an OWNER account with no review. **Decide:** is unmoderated signup the intended product behaviour, or is an approval flow genuinely required and simply never built? This materially changes Phase 2's scope. Nothing was added or removed pending your answer.

3. **`users` + `farm_users` were merged into one table** by `V4`, at your instruction this session. The ERD target still lists them separately. **Confirm** the merge is authoritative (in which case the ERD must be updated in Phase 2), and accept the consequence: **a user now belongs to exactly one farm with one role.** Multi-farm membership is no longer structurally possible.

4. **Two competing deactivation mechanisms exist.** `status` (`ACTIVE`/`INACTIVE`, dead — written once, never read, gates nothing) and `is_deleted` (live, enforced by `@SQLRestriction`, drives the delete endpoint). **Decide:** drop the `status` column, or implement it and define how it interacts with `is_deleted`. Leaving both is how the next audit finds the same drift.

5. **Login response policy for deactivated users** (the Task 4 defect). A deactivated user is told their password is wrong. Making that distinguishable improves usability but leaks account existence, contradicting the deliberate anti-enumeration design of `forgot-password`. **Decide** the policy: (a) keep the generic 401 and accept the support burden, (b) return a distinct `403` + `errorCode` and accept enumeration on login, or (c) distinguish only *after* the password is verified correct — which reveals nothing an attacker who already has the password doesn't know. **(c) is the standard resolution and is recommended**, but it is your call.

6. **`errorCode` is never populated.** Confirm whether the Angular frontend needs machine-readable failure reasons. If yes, populating `errorCode` should be a Phase 2 cross-cutting task rather than something retrofitted per module.

7. **`assets` has no owner in the Phase 2 module list.** The brief lists Feed, Water Quality, Task Completions, Reminders, and Finance as the modules to complete — but `assets` (ERD group 3) is equally absent and unlisted. **Decide:** in scope for Phase 2, or deliberately deferred?

8. **`species` and `farms` have no API.** Both have entities but no controller or resolver: species cannot be listed or added (only the 2 seeded rows are usable), and farms can only ever be created as a side effect of signup. Neither is in the Phase 2 list. **Decide:** in scope, or deferred?

9. **Dead permissions.** `mark_task_done` and `view_finance` are seeded and assigned to roles but enforced nowhere. They will become live automatically when Task Completions and Finance are built in Phase 2 — **confirm** the intended enforcement points are exactly those modules, so seeding is not changed unnecessarily.

10. **`V2` audit columns do not cover the absent modules' tables.** None of `assets`, `feed_*`, `water_quality_logs`, `task_completions`, `reminders`, `costs`, `customers`, `sales` have `created_at`/`updated_at`/`is_deleted`/`updated_by`/`deleted_by`. **Decide:** should Phase 2 entities extend `BaseEntity` (requiring a new migration to add those columns to all 10 tables — recommended for consistency), or be plain entities without audit/soft-delete?

11. **Production hardening, unrelated to module gaps but worth a decision now:** GraphiQL is enabled (`spring.graphql.graphiql.enabled: true`); the JWT secret and DB password have insecure literal defaults in `application.yml`; `SmsSender` is a logging stub so **no OTP is actually delivered**; `/actuator/health` is permitted but the actuator dependency is absent; and login has no rate limiting.

---

## Phase 1 completion statement

No application code, migrations, configuration, or seed data were created, modified, or deleted during this audit. The only file written is this document. Live-database interaction was read-only except for the end-to-end verification data noted below.

**Test data left in the database from earlier verification in this session** (not created by the audit itself): farm 7 "Shamba la Ukaguzi" and three users — `Mkaguzi` (OWNER), `Mkaguzi wa Pili` (OWNER), `Mfanyakazi Mkaguzi` (soft-deleted). Removable on request.

**Phase 2 has not been started and will not be, pending review of this document.**
