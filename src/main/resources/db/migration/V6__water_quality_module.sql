-- ============================================================
-- MODULE: Water Quality (water_quality_logs)
-- Jedwali lilitengenezwa na V1; halikuwa na entity ya JPA.
-- ============================================================

ALTER TABLE water_quality_logs
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES farm_users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES farm_users(user_id);

-- V1 tayari ina idx_water_unit_date(unit_id, log_date) - hakuna index mpya inayohitajika.

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('log_water_quality', 'FARM', 'WATER', 'Kurekodi vipimo vya ubora wa maji')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- Angalia V5 kwa maelezo ya sharti la EXISTS (kutofautisha DB mpya na
-- inayotumika, kwa sababu RbacSeedService inaruka role zilizo na ruhusa).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'log_water_quality'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER', 'WORKER')
ON CONFLICT DO NOTHING;
