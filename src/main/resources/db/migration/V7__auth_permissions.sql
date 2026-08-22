-- ============================================================
-- B4 + B7: ruhusa mbili mpya za awamu hii.
--
--   approve_users - kuidhinisha waliojisajili (PENDING_APPROVAL -> ACTIVE)
--   manage_farms  - kuunda/kuorodhesha mashamba
--
-- Idhini inadhibitiwa na RUHUSA, si jina la role - hivyo role yoyote
-- iliyopewa approve_users inaweza kuidhinisha (angalia PermissionChecker).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('approve_users', 'UAA',  'USER_MANAGEMENT', 'Kuidhinisha watumiaji waliojisajili'),
    ('manage_farms',  'FARM', 'FARM_MANAGEMENT', 'Kuunda na kuorodhesha mashamba')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- Kuziunganisha na roles ZILIZOPO.
--
-- Sharti la EXISTS linatofautisha mazingira mawili:
--   * DB inayotumika -> role tayari zina ruhusa, na RbacSeedService
--     inaruka role yenye ruhusa yoyote - hivyo mistari mipya ya CSV
--     HAINGETUMIKA KAMWE. Hapa ndipo zinapowekwa.
--   * DB mpya kabisa  -> role hazina ruhusa bado -> hakuna kinachoingizwa
--     hapa, na RbacSeedService inaziweka zote kutoka CSV.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code IN ('approve_users', 'manage_farms')
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name = 'OWNER'
ON CONFLICT DO NOTHING;
