-- ============================================================
-- MODULE: Feed (feed_purchases, feeding_logs, feed_stock_movements)
--
-- Majedwali yenyewe yalitengenezwa na V1; hayakuwa na entity za JPA.
-- Migration hii inayaandaa kwa entity zinazorithi BaseEntity (audit +
-- soft-delete), na inaongeza ruhusa mbili mpya za module hii.
-- ============================================================

-- ---------- 1. Audit + soft-delete (sawa na V2 kwa majedwali mengine) ----------
ALTER TABLE feed_purchases
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES farm_users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES farm_users(user_id);

ALTER TABLE feeding_logs
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES farm_users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES farm_users(user_id);

ALTER TABLE feed_stock_movements
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES farm_users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES farm_users(user_id);

-- Query kuu za module: "chakula cha shamba hili", "kulisha kwa mzunguko huu"
CREATE INDEX idx_feed_purchases_farm ON feed_purchases(farm_id);
CREATE INDEX idx_feeding_logs_cycle ON feeding_logs(cycle_id, log_date);
CREATE INDEX idx_feed_stock_movements_farm ON feed_stock_movements(farm_id);

-- ---------- 2. Ruhusa mpya ----------
-- Zinaingizwa hapa (si kwa CSV pekee) kwa sababu RbacSeedService inapakia
-- permissions kwa existsByCode - hivyo hizi zitarukwa wakati wa seeding,
-- na hakuna rudufu. CSV bado inasasishwa kama katalogi ya binadamu.
INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('log_feeding',       'FARM', 'FEED', 'Kurekodi ulishaji wa kila siku'),
    ('manage_feed_stock', 'FARM', 'FEED', 'Kununua chakula na kusimamia stoo')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- ---------- 3. Kuunganisha ruhusa mpya na roles ZILIZOPO ----------
-- MUHIMU: RbacSeedService.seedRolePermissions() inaruka role yoyote ambayo
-- TAYARI ina ruhusa - hivyo kwenye database inayotumika, mistari mipya ya
-- role_permissions.csv HAINGETUMIKA KAMWE. Hapa ndipo zinapowekwa.
--
-- Sharti la `EXISTS (... role_permissions ...)` linatofautisha mazingira:
--   * DB inayotumika  -> role tayari zina ruhusa -> mistari inaingizwa hapa.
--   * DB mpya kabisa  -> role hazina ruhusa bado -> hakuna kinachoingizwa,
--                        na RbacSeedService inaziweka zote kutoka CSV.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('log_feeding', 'manage_feed_stock')
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND (
        (p.code = 'log_feeding'       AND r.name IN ('OWNER', 'FARM_MANAGER', 'WORKER'))
     OR (p.code = 'manage_feed_stock' AND r.name IN ('OWNER', 'FARM_MANAGER'))
  )
ON CONFLICT DO NOTHING;
