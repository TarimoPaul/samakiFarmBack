-- ============================================================
-- Mfumo wa Ufugaji wa Samaki — Migration ya Kwanza
-- Kutoka ERD_Muundo_wa_Database.mermaid
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------- RBAC ----------
CREATE TABLE roles (
    role_id     SERIAL PRIMARY KEY,
    name        VARCHAR(50) UNIQUE NOT NULL,   -- OWNER, FARM_MANAGER, WORKER, VIEWER
    description TEXT
);

CREATE TABLE permissions (
    permission_id SERIAL PRIMARY KEY,
    code          VARCHAR(80) UNIQUE NOT NULL, -- mfano: view_finance, edit_cycle
    module        VARCHAR(50) NOT NULL DEFAULT 'FARM',
    group_name    VARCHAR(50),
    description   TEXT
);

CREATE TABLE role_permissions (
    role_id       INT REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id INT REFERENCES permissions(permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    user_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name          VARCHAR(150) NOT NULL,
    phone         VARCHAR(20) UNIQUE NOT NULL,
    email         VARCHAR(150) UNIQUE,
    password_hash TEXT NOT NULL,
    push_token    TEXT,              -- FCM/Web-push token kwa reminders
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_root       BOOLEAN NOT NULL DEFAULT false, -- ROOT bypass (kama Lsms) - huru na role/farm
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- FARMS / SCOPE ----------
CREATE TABLE farms (
    farm_id       SERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    location      VARCHAR(200),
    owner_user_id UUID REFERENCES users(user_id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE farm_users (
    farm_id INT REFERENCES farms(farm_id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
    role_id INT REFERENCES roles(role_id),
    PRIMARY KEY (farm_id, user_id)
);

-- ---------- ASSETS / PRODUCTION UNITS ----------
CREATE TABLE assets (
    asset_id      SERIAL PRIMARY KEY,
    farm_id       INT REFERENCES farms(farm_id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    category      VARCHAR(80),        -- Kifaa, Jengo, Gari, n.k.
    purchase_date DATE,
    value         NUMERIC(14,2),
    status        VARCHAR(30) DEFAULT 'ACTIVE'
);

CREATE TABLE production_units (
    unit_id      SERIAL PRIMARY KEY,
    farm_id      INT REFERENCES farms(farm_id) ON DELETE CASCADE,
    code         VARCHAR(30) NOT NULL,        -- mfano T1
    type         VARCHAR(20) NOT NULL CHECK (type IN ('TANK','POND','BWAWA')),
    size_m3      NUMERIC(10,2),
    water_source VARCHAR(100),
    status       VARCHAR(20) NOT NULL DEFAULT 'IDLE',  -- IDLE / ACTIVE / MAINTENANCE
    UNIQUE (farm_id, code)
);

-- ---------- SPECIES / CYCLES ----------
CREATE TABLE species (
    species_id           SERIAL PRIMARY KEY,
    name                 VARCHAR(80) UNIQUE NOT NULL,   -- Sato, Kambale
    growth_months_avg    NUMERIC(4,1) NOT NULL,
    avg_harvest_weight_kg NUMERIC(6,2) NOT NULL
);

CREATE TABLE cycles (
    cycle_id               SERIAL PRIMARY KEY,
    unit_id                INT REFERENCES production_units(unit_id) ON DELETE CASCADE,
    species_id             INT REFERENCES species(species_id),
    stocking_date           DATE NOT NULL,
    fingerlings_count       INT NOT NULL,
    survival_rate_estimate  NUMERIC(4,2) DEFAULT 0.85,
    expected_harvest_date   DATE,          -- inakokotolewa na app/trigger
    actual_harvest_date     DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE/HARVESTED/FAILED
);

-- ---------- FEED ----------
CREATE TABLE feed_purchases (
    purchase_id   SERIAL PRIMARY KEY,
    farm_id       INT REFERENCES farms(farm_id) ON DELETE CASCADE,
    purchase_date DATE NOT NULL,
    feed_type     VARCHAR(80) NOT NULL,
    quantity_kg   NUMERIC(10,2) NOT NULL,
    unit_cost     NUMERIC(12,2) NOT NULL,
    total_cost    NUMERIC(14,2) GENERATED ALWAYS AS (quantity_kg * unit_cost) STORED,
    supplier      VARCHAR(150)
);

CREATE TABLE feeding_logs (
    log_id            SERIAL PRIMARY KEY,
    cycle_id          INT REFERENCES cycles(cycle_id) ON DELETE CASCADE,
    log_date          DATE NOT NULL,
    feed_type         VARCHAR(80),
    quantity_kg       NUMERIC(8,2) NOT NULL,
    recorded_by_user_id UUID REFERENCES users(user_id)
);

CREATE TABLE feed_stock_movements (
    movement_id              SERIAL PRIMARY KEY,
    farm_id                  INT REFERENCES farms(farm_id) ON DELETE CASCADE,
    direction                VARCHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    quantity_kg              NUMERIC(10,2) NOT NULL,
    reference_purchase_id    INT REFERENCES feed_purchases(purchase_id),
    reference_feeding_log_id INT REFERENCES feeding_logs(log_id),
    moved_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- WATER QUALITY ----------
CREATE TABLE water_quality_logs (
    log_id             SERIAL PRIMARY KEY,
    unit_id            INT REFERENCES production_units(unit_id) ON DELETE CASCADE,
    log_date           DATE NOT NULL,
    ph                 NUMERIC(3,1),
    temperature        NUMERIC(4,1),
    oxygen             NUMERIC(4,1),
    notes              TEXT,
    recorded_by_user_id UUID REFERENCES users(user_id)
);

-- ---------- DAILY TASKS / REMINDERS ----------
CREATE TABLE daily_tasks (
    task_id         SERIAL PRIMARY KEY,
    cycle_id        INT REFERENCES cycles(cycle_id) ON DELETE CASCADE,
    task_type       VARCHAR(50) NOT NULL,   -- Kulisha, Kuangalia Maji, Kusafisha
    scheduled_time  TIME NOT NULL,
    frequency       VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    assigned_role_id INT REFERENCES roles(role_id)
);

CREATE TABLE task_completions (
    completion_id       SERIAL PRIMARY KEY,
    task_id             INT REFERENCES daily_tasks(task_id) ON DELETE CASCADE,
    completion_date     DATE NOT NULL,
    completed_by_user_id UUID REFERENCES users(user_id),
    completed_at        TIMESTAMPTZ,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/DONE/MISSED/LATE
    notes                TEXT,
    UNIQUE (task_id, completion_date)
);

CREATE TABLE reminders (
    reminder_id SERIAL PRIMARY KEY,
    task_id     INT REFERENCES daily_tasks(task_id) ON DELETE CASCADE,
    channel     VARCHAR(10) NOT NULL CHECK (channel IN ('PUSH','SMS')),
    send_time   TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' -- PENDING/SENT/FAILED
);

-- ---------- FINANCE ----------
CREATE TABLE costs (
    cost_id     SERIAL PRIMARY KEY,
    cycle_id    INT REFERENCES cycles(cycle_id) ON DELETE CASCADE,
    category    VARCHAR(50) NOT NULL,
    description TEXT,
    amount      NUMERIC(14,2) NOT NULL,
    cost_date   DATE NOT NULL
);

CREATE TABLE customers (
    customer_id SERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    phone       VARCHAR(20),
    type        VARCHAR(30) -- Mgahawa/Hoteli/Mchuuzi/Binafsi
);

CREATE TABLE sales (
    sale_id        SERIAL PRIMARY KEY,
    cycle_id       INT REFERENCES cycles(cycle_id) ON DELETE CASCADE,
    customer_id    INT REFERENCES customers(customer_id),
    sale_date      DATE NOT NULL,
    kg             NUMERIC(10,2) NOT NULL,
    price_per_kg   NUMERIC(12,2) NOT NULL,
    total_amount   NUMERIC(14,2) GENERATED ALWAYS AS (kg * price_per_kg) STORED
);

-- ---------- INDEXES MUHIMU ----------
CREATE INDEX idx_cycles_unit ON cycles(unit_id);
CREATE INDEX idx_cycles_status ON cycles(status);
CREATE INDEX idx_tasks_cycle ON daily_tasks(cycle_id);
CREATE INDEX idx_completions_date ON task_completions(completion_date);
CREATE INDEX idx_costs_cycle ON costs(cycle_id);
CREATE INDEX idx_sales_cycle ON sales(cycle_id);
CREATE INDEX idx_water_unit_date ON water_quality_logs(unit_id, log_date);

-- ---------- SEED DATA YA MSINGI ----------
-- Roles nne za msingi zinabaki hardcoded hapa kwa sababu shamba jipya
-- linahitaji OWNER inayofanya kazi mara moja wakati wa signup (tofauti na Lsms
-- ambapo mfumo ni wa taasisi moja na ROOT-pekee ndiye anaunda roles zote kupitia
-- UI). Roles za ziada zinaweza kutengenezwa wakati wowote kupitia POST /api/roles.
INSERT INTO roles (name, description) VALUES
('OWNER', 'Msimamizi mkuu wa shamba'),
('FARM_MANAGER', 'Meneja wa shamba mahususi'),
('WORKER', 'Mfanyakazi wa kila siku'),
('VIEWER', 'Anaona ripoti tu');

-- Permissions HAZIINGIZWI hapa tena (kama Lsms): zinapakiwa na kudumishwa na
-- RbacDataInitializer kutoka seed/permissions.csv (idempotent), na uhusiano wa
-- role<->permission unapakiwa kutoka seed/role_permissions.csv MARA MOJA TU kwa
-- kila role isiyo na ruhusa yoyote bado (angalia RbacDataInitializer).

INSERT INTO species (name, growth_months_avg, avg_harvest_weight_kg) VALUES
('Sato', 7, 0.35),
('Kambale', 6, 1.0);
