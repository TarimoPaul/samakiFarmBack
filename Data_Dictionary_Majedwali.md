# Data Dictionary - Majedwali ya Mfumo wa Ufugaji wa Samaki

> **IMEZALISHWA KIOTOMATIKI - USIIHARIRI KWA MKONO.**
> Chanzo: database halisi. Izalishe upya baada ya kila migration:
> `./tools/generate-docs.ps1`
>
> Toleo la 2026-08-24. Jedwali: **21**. Safu: **201**.

## Migrations zilizotumika

- **V1** - init schema _(2026-08-20)_
- **V2** - add audit and soft delete columns _(2026-08-20)_
- **V3** - add password reset otps _(2026-08-20)_
- **V4** - merge users into farm users _(2026-08-22)_
- **V5** - unmerge users and farm users _(2026-08-22)_
- **V6** - user account lifecycle _(2026-08-22)_
- **V7** - auth permissions _(2026-08-22)_
- **V8** - feed module _(2026-08-22)_
- **V9** - farm name unique _(2026-08-24)_

## Muhtasari

| # | Kikundi | Jedwali |
|---|---------|---------|
| 1 | 1. RBAC / UAA | `users`, `roles`, `permissions`, `role_permissions`, `password_reset_otps` |
| 2 | 2. Mashamba na uanachama | `farms`, `farm_users` |
| 3 | 3. Rasilimali na vitengo | `production_units`, `assets` |
| 4 | 4. Aina za samaki na mizunguko | `species`, `cycles` |
| 5 | 5. Chakula | `feed_purchases`, `feeding_logs`, `feed_stock_movements` |
| 6 | 6. Ubora wa maji | `water_quality_logs` |
| 7 | 7. Kazi na vikumbusho | `daily_tasks`, `task_completions`, `reminders` |
| 8 | 8. Fedha | `costs`, `sales`, `customers` |

---

## 1. RBAC / UAA

### `users`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `user_id` | uuid | NOT NULL | uuid_generate_v4() |
| `name` | varchar(150) | NOT NULL |  |
| `phone` | varchar(20) | NOT NULL |  |
| `email` | varchar(150) |  |  |
| `password_hash` | text | NOT NULL |  |
| `push_token` | text |  |  |
| `status` | varchar(20) | NOT NULL | 'PENDING_APPROVAL' |
| `is_root` | boolean | NOT NULL | false |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |
| `must_change_password` | boolean | NOT NULL | false |

**Vikwazo:**

- **UNIQUE** `users_email_key` - UNIQUE (email)
- **UNIQUE** `users_phone_key` - UNIQUE (phone)
- **PK** `users_pkey` - PRIMARY KEY (user_id)
- **FK** `users_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `users_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)
- **CHECK** `users_status_check` - CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))

**Index:**

- `idx_users_status`

### `roles`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `role_id` | integer | NOT NULL | nextval('roles_role_id_seq') |
| `name` | varchar(50) | NOT NULL |  |
| `description` | text |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **UNIQUE** `roles_name_key` - UNIQUE (name)
- **PK** `roles_pkey` - PRIMARY KEY (role_id)
- **FK** `roles_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `roles_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

### `permissions`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `permission_id` | integer | NOT NULL | nextval('permissions_permission_id_seq') |
| `code` | varchar(80) | NOT NULL |  |
| `module` | varchar(50) | NOT NULL | 'FARM' |
| `group_name` | varchar(50) |  |  |
| `description` | text |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **UNIQUE** `permissions_code_key` - UNIQUE (code)
- **PK** `permissions_pkey` - PRIMARY KEY (permission_id)
- **FK** `permissions_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `permissions_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

### `role_permissions`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `role_id` | integer | NOT NULL |  |
| `permission_id` | integer | NOT NULL |  |

**Vikwazo:**

- **PK** `role_permissions_pkey` - PRIMARY KEY (role_id, permission_id)
- **FK** `role_permissions_permission_id_fkey` - FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
- **FK** `role_permissions_role_id_fkey` - FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE

### `password_reset_otps`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `otp_id` | bigint | NOT NULL | nextval('password_reset_otps_otp_id_seq') |
| `user_id` | uuid | NOT NULL |  |
| `code_hash` | text | NOT NULL |  |
| `expires_at` | timestamptz | NOT NULL |  |
| `attempts` | integer | NOT NULL | 0 |
| `used_at` | timestamptz |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `password_reset_otps_pkey` - PRIMARY KEY (otp_id)
- **FK** `password_reset_otps_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `password_reset_otps_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)
- **FK** `password_reset_otps_user_id_fkey` - FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE

**Index:**

- `idx_password_reset_otps_user_created`

---

## 2. Mashamba na uanachama

### `farms`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `farm_id` | integer | NOT NULL | nextval('farms_farm_id_seq') |
| `name` | varchar(150) | NOT NULL |  |
| `location` | varchar(200) |  |  |
| `owner_user_id` | uuid |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **UNIQUE** `uq_farms_name` - UNIQUE (name)
- **PK** `farms_pkey` - PRIMARY KEY (farm_id)
- **FK** `farms_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `farms_owner_user_id_fkey` - FOREIGN KEY (owner_user_id) REFERENCES users(user_id)
- **FK** `farms_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

### `farm_users`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `user_id` | uuid | NOT NULL |  |
| `farm_id` | integer | NOT NULL |  |
| `role_id` | integer |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `farm_users_pkey` - PRIMARY KEY (user_id, farm_id)
- **FK** `farm_users_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `farm_users_farm_id_fkey` - FOREIGN KEY (farm_id) REFERENCES farms(farm_id) ON DELETE CASCADE
- **FK** `farm_users_role_id_fkey` - FOREIGN KEY (role_id) REFERENCES roles(role_id)
- **FK** `farm_users_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)
- **FK** `farm_users_user_id_fkey` - FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE

**Index:**

- `idx_farm_users_farm`

---

## 3. Rasilimali na vitengo

### `production_units`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `unit_id` | integer | NOT NULL | nextval('production_units_unit_id_seq') |
| `farm_id` | integer |  |  |
| `code` | varchar(30) | NOT NULL |  |
| `type` | varchar(20) | NOT NULL |  |
| `size_m3` | numeric(10,2) |  |  |
| `water_source` | varchar(100) |  |  |
| `status` | varchar(20) | NOT NULL | 'IDLE' |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **UNIQUE** `production_units_farm_id_code_key` - UNIQUE (farm_id, code)
- **PK** `production_units_pkey` - PRIMARY KEY (unit_id)
- **FK** `production_units_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `production_units_farm_id_fkey` - FOREIGN KEY (farm_id) REFERENCES farms(farm_id) ON DELETE CASCADE
- **FK** `production_units_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)
- **CHECK** `production_units_type_check` - CHECK (((type)::text = ANY ((ARRAY['TANK'::character varying, 'POND'::character varying, 'BWAWA'::character varying])::text[])))

### `assets`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `asset_id` | integer | NOT NULL | nextval('assets_asset_id_seq') |
| `farm_id` | integer |  |  |
| `name` | varchar(150) | NOT NULL |  |
| `category` | varchar(80) |  |  |
| `purchase_date` | date |  |  |
| `value` | numeric(14,2) |  |  |
| `status` | varchar(30) |  | 'ACTIVE' |

**Vikwazo:**

- **PK** `assets_pkey` - PRIMARY KEY (asset_id)
- **FK** `assets_farm_id_fkey` - FOREIGN KEY (farm_id) REFERENCES farms(farm_id) ON DELETE CASCADE

---

## 4. Aina za samaki na mizunguko

### `species`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `species_id` | integer | NOT NULL | nextval('species_species_id_seq') |
| `name` | varchar(80) | NOT NULL |  |
| `growth_months_avg` | numeric(4,1) | NOT NULL |  |
| `avg_harvest_weight_kg` | numeric(6,2) | NOT NULL |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **UNIQUE** `species_name_key` - UNIQUE (name)
- **PK** `species_pkey` - PRIMARY KEY (species_id)
- **FK** `species_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `species_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

### `cycles`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `cycle_id` | integer | NOT NULL | nextval('cycles_cycle_id_seq') |
| `unit_id` | integer |  |  |
| `species_id` | integer |  |  |
| `stocking_date` | date | NOT NULL |  |
| `fingerlings_count` | integer | NOT NULL |  |
| `survival_rate_estimate` | numeric(4,2) |  | 0.85 |
| `expected_harvest_date` | date |  |  |
| `actual_harvest_date` | date |  |  |
| `status` | varchar(20) | NOT NULL | 'ACTIVE' |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `cycles_pkey` - PRIMARY KEY (cycle_id)
- **FK** `cycles_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `cycles_species_id_fkey` - FOREIGN KEY (species_id) REFERENCES species(species_id)
- **FK** `cycles_unit_id_fkey` - FOREIGN KEY (unit_id) REFERENCES production_units(unit_id) ON DELETE CASCADE
- **FK** `cycles_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

**Index:**

- `idx_cycles_status`
- `idx_cycles_unit`

---

## 5. Chakula

### `feed_purchases`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `purchase_id` | integer | NOT NULL | nextval('feed_purchases_purchase_id_seq') |
| `farm_id` | integer |  |  |
| `purchase_date` | date | NOT NULL |  |
| `feed_type` | varchar(80) | NOT NULL |  |
| `quantity_kg` | numeric(10,2) | NOT NULL |  |
| `unit_cost` | numeric(12,2) | NOT NULL |  |
| `total_cost` | numeric(14,2) |  | GENERATED |
| `supplier` | varchar(150) |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `feed_purchases_pkey` - PRIMARY KEY (purchase_id)
- **FK** `feed_purchases_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `feed_purchases_farm_id_fkey` - FOREIGN KEY (farm_id) REFERENCES farms(farm_id) ON DELETE CASCADE
- **FK** `feed_purchases_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

**Index:**

- `idx_feed_purchases_farm`

### `feeding_logs`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `log_id` | integer | NOT NULL | nextval('feeding_logs_log_id_seq') |
| `cycle_id` | integer |  |  |
| `log_date` | date | NOT NULL |  |
| `feed_type` | varchar(80) |  |  |
| `quantity_kg` | numeric(8,2) | NOT NULL |  |
| `recorded_by_user_id` | uuid |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `feeding_logs_pkey` - PRIMARY KEY (log_id)
- **FK** `feeding_logs_cycle_id_fkey` - FOREIGN KEY (cycle_id) REFERENCES cycles(cycle_id) ON DELETE CASCADE
- **FK** `feeding_logs_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `feeding_logs_recorded_by_user_id_fkey` - FOREIGN KEY (recorded_by_user_id) REFERENCES users(user_id)
- **FK** `feeding_logs_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

**Index:**

- `idx_feeding_logs_cycle`

### `feed_stock_movements`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `movement_id` | integer | NOT NULL | nextval('feed_stock_movements_movement_id_seq') |
| `farm_id` | integer |  |  |
| `direction` | varchar(3) | NOT NULL |  |
| `quantity_kg` | numeric(10,2) | NOT NULL |  |
| `reference_purchase_id` | integer |  |  |
| `reference_feeding_log_id` | integer |  |  |
| `moved_at` | timestamptz | NOT NULL | now() |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `feed_stock_movements_pkey` - PRIMARY KEY (movement_id)
- **FK** `feed_stock_movements_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `feed_stock_movements_farm_id_fkey` - FOREIGN KEY (farm_id) REFERENCES farms(farm_id) ON DELETE CASCADE
- **FK** `feed_stock_movements_reference_feeding_log_id_fkey` - FOREIGN KEY (reference_feeding_log_id) REFERENCES feeding_logs(log_id)
- **FK** `feed_stock_movements_reference_purchase_id_fkey` - FOREIGN KEY (reference_purchase_id) REFERENCES feed_purchases(purchase_id)
- **FK** `feed_stock_movements_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)
- **CHECK** `feed_stock_movements_direction_check` - CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))

**Index:**

- `idx_feed_stock_movements_farm`

---

## 6. Ubora wa maji

### `water_quality_logs`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `log_id` | integer | NOT NULL | nextval('water_quality_logs_log_id_seq') |
| `unit_id` | integer |  |  |
| `log_date` | date | NOT NULL |  |
| `ph` | numeric(3,1) |  |  |
| `temperature` | numeric(4,1) |  |  |
| `oxygen` | numeric(4,1) |  |  |
| `notes` | text |  |  |
| `recorded_by_user_id` | uuid |  |  |

**Vikwazo:**

- **PK** `water_quality_logs_pkey` - PRIMARY KEY (log_id)
- **FK** `water_quality_logs_recorded_by_user_id_fkey` - FOREIGN KEY (recorded_by_user_id) REFERENCES users(user_id)
- **FK** `water_quality_logs_unit_id_fkey` - FOREIGN KEY (unit_id) REFERENCES production_units(unit_id) ON DELETE CASCADE

**Index:**

- `idx_water_unit_date`

---

## 7. Kazi na vikumbusho

### `daily_tasks`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `task_id` | integer | NOT NULL | nextval('daily_tasks_task_id_seq') |
| `cycle_id` | integer |  |  |
| `task_type` | varchar(50) | NOT NULL |  |
| `scheduled_time` | time | NOT NULL |  |
| `frequency` | varchar(20) | NOT NULL | 'DAILY' |
| `assigned_role_id` | integer |  |  |
| `created_at` | timestamptz | NOT NULL | now() |
| `updated_at` | timestamptz |  |  |
| `deleted_at` | timestamptz |  |  |
| `is_deleted` | boolean | NOT NULL | false |
| `updated_by` | uuid |  |  |
| `deleted_by` | uuid |  |  |

**Vikwazo:**

- **PK** `daily_tasks_pkey` - PRIMARY KEY (task_id)
- **FK** `daily_tasks_assigned_role_id_fkey` - FOREIGN KEY (assigned_role_id) REFERENCES roles(role_id)
- **FK** `daily_tasks_cycle_id_fkey` - FOREIGN KEY (cycle_id) REFERENCES cycles(cycle_id) ON DELETE CASCADE
- **FK** `daily_tasks_deleted_by_fkey` - FOREIGN KEY (deleted_by) REFERENCES users(user_id)
- **FK** `daily_tasks_updated_by_fkey` - FOREIGN KEY (updated_by) REFERENCES users(user_id)

**Index:**

- `idx_tasks_cycle`

### `task_completions`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `completion_id` | integer | NOT NULL | nextval('task_completions_completion_id_seq') |
| `task_id` | integer |  |  |
| `completion_date` | date | NOT NULL |  |
| `completed_by_user_id` | uuid |  |  |
| `completed_at` | timestamptz |  |  |
| `status` | varchar(20) | NOT NULL | 'PENDING' |
| `notes` | text |  |  |

**Vikwazo:**

- **UNIQUE** `task_completions_task_id_completion_date_key` - UNIQUE (task_id, completion_date)
- **PK** `task_completions_pkey` - PRIMARY KEY (completion_id)
- **FK** `task_completions_completed_by_user_id_fkey` - FOREIGN KEY (completed_by_user_id) REFERENCES users(user_id)
- **FK** `task_completions_task_id_fkey` - FOREIGN KEY (task_id) REFERENCES daily_tasks(task_id) ON DELETE CASCADE

**Index:**

- `idx_completions_date`

### `reminders`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `reminder_id` | integer | NOT NULL | nextval('reminders_reminder_id_seq') |
| `task_id` | integer |  |  |
| `channel` | varchar(10) | NOT NULL |  |
| `send_time` | timestamptz | NOT NULL |  |
| `status` | varchar(20) | NOT NULL | 'PENDING' |

**Vikwazo:**

- **PK** `reminders_pkey` - PRIMARY KEY (reminder_id)
- **FK** `reminders_task_id_fkey` - FOREIGN KEY (task_id) REFERENCES daily_tasks(task_id) ON DELETE CASCADE
- **CHECK** `reminders_channel_check` - CHECK (((channel)::text = ANY ((ARRAY['PUSH'::character varying, 'SMS'::character varying])::text[])))

---

## 8. Fedha

### `costs`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `cost_id` | integer | NOT NULL | nextval('costs_cost_id_seq') |
| `cycle_id` | integer |  |  |
| `category` | varchar(50) | NOT NULL |  |
| `description` | text |  |  |
| `amount` | numeric(14,2) | NOT NULL |  |
| `cost_date` | date | NOT NULL |  |

**Vikwazo:**

- **PK** `costs_pkey` - PRIMARY KEY (cost_id)
- **FK** `costs_cycle_id_fkey` - FOREIGN KEY (cycle_id) REFERENCES cycles(cycle_id) ON DELETE CASCADE

**Index:**

- `idx_costs_cycle`

### `sales`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `sale_id` | integer | NOT NULL | nextval('sales_sale_id_seq') |
| `cycle_id` | integer |  |  |
| `customer_id` | integer |  |  |
| `sale_date` | date | NOT NULL |  |
| `kg` | numeric(10,2) | NOT NULL |  |
| `price_per_kg` | numeric(12,2) | NOT NULL |  |
| `total_amount` | numeric(14,2) |  | GENERATED |

**Vikwazo:**

- **PK** `sales_pkey` - PRIMARY KEY (sale_id)
- **FK** `sales_customer_id_fkey` - FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
- **FK** `sales_cycle_id_fkey` - FOREIGN KEY (cycle_id) REFERENCES cycles(cycle_id) ON DELETE CASCADE

**Index:**

- `idx_sales_cycle`

### `customers`

| Safu | Aina | Null | Default |
|------|------|------|---------|
| `customer_id` | integer | NOT NULL | nextval('customers_customer_id_seq') |
| `name` | varchar(150) | NOT NULL |  |
| `phone` | varchar(20) |  |  |
| `type` | varchar(30) |  |  |

