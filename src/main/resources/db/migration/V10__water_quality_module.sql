-- ============================================================
-- MODULE: Water Quality (water_quality_logs)
--
-- Jedwali lenyewe lilitengenezwa na V1 na LINABAKI KAMA LILIVYO: unit_id,
-- log_date, ph, temperature, oxygen, notes, recorded_by_user_id. Hakuna
-- safu mpya ya data inayoongezwa hapa - muundo ni ule ule wa ERD na
-- Data Dictionary.
--
-- Migration hii inafanya mambo mawili tu, kwa mtindo ule ule wa V8 (Feed):
--   1. Kuliandaa jedwali kwa entity inayorithi BaseEntity (audit +
--      soft-delete), kama V8 ilivyofanya kwa majedwali matatu ya chakula.
--   2. Kuongeza ruhusa ya module hii na kuiunganisha na roles zilizopo.
--
-- MUHIMU: updated_by/deleted_by zinaelekea `users` (MTU), si `farm_users`
-- (uanachama) - angalia V5__unmerge_users_and_farm_users.sql.
-- ============================================================

-- ---------- 1. Audit + soft-delete (sawa na V2/V8 kwa majedwali mengine) ----------
-- water_quality_logs ndilo jedwali PEKEE la uzalishaji lililokuwa halina
-- safu hizi - V2 iliruka, na hakukuwa na module ya kuzihitaji hadi sasa.
-- Bila created_at, vipimo viwili vya siku moja havina mpangilio wowote wa
-- uhakika; bila is_deleted, entity hii ingekuwa pekee inayotofautiana na
-- nyingine zote kwenye repo.
ALTER TABLE water_quality_logs
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

-- idx_water_unit_date (unit_id, log_date) tayari ipo tangu V1 - ndiyo
-- query kuu ya module hii ("vipimo vya tanki hili"), hivyo haihitaji index
-- nyingine.

-- ---------- 2. Ruhusa mpya ----------
-- Sababu ile ile ya V8: RbacSeedService inapakia permissions kwa
-- existsByCode, hivyo hizi zitarukwa wakati wa seeding na hakuna rudufu.
-- CSV bado inasasishwa kama katalogi ya binadamu.
--
-- MOJA tu, si mbili: kurekodi kipimo ni kitendo kimoja. Kusoma vipimo
-- kunatumia view_dashboard, kama module ya chakula inavyofanya
-- (FeedService.listFeedingLogs) - hakuna ruhusa mpya ya kusoma.
INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('log_water_quality', 'FARM', 'WATER', 'Kurekodi vipimo vya ubora wa maji')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

-- ---------- 3. Kuunganisha ruhusa mpya na roles ZILIZOPO ----------
-- Sharti la EXISTS linatofautisha mazingira, kama V8:
--   * DB inayotumika -> role tayari zina ruhusa -> mstari unaingizwa hapa.
--   * DB mpya kabisa -> role hazina ruhusa bado -> hakuna kinachoingizwa,
--                       na RbacSeedService inaziweka zote kutoka CSV.
--
-- WORKER ANAIPATA: kupima maji ni kazi ya kila siku ya shambani, kama
-- kulisha (log_feeding). VIEWER HAIPATI: yeye ni ripoti tu.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'log_water_quality'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER', 'WORKER')
ON CONFLICT DO NOTHING;
