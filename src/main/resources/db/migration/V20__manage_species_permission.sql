-- ============================================================
-- Ruhusa mpya: kusimamia katalogi ya aina za samaki (`manage_species`).
--
-- KWA NINI RUHUSA MPYA KABISA. Hadi sasa `species` ilikuwa YA KUSOMA
-- PEKEE: aina mbili za V1 (Sato, Kambale) zilipandwa na migration, na
-- SpeciesService ilisema wazi kwamba kuunda si kazi ya kila siku ya
-- shamba. Matokeo yake mkulima anayefuga aina ya tatu hakuwa na njia
-- yoyote ya kuiongeza - ilibidi mtu aguse database. Mutation ya
-- createSpecies inaifungua njia hiyo, na ruhusa hii ndiyo inayoilinda.
--
-- HAKUNA RUHUSA ILIYOKUWEPO INAYOFAA, na kila mgombea alikuwa na kasoro:
--
--   * `view_dashboard` - ndiyo inayolinda query ya `species`, lakini ni
--     ruhusa ya KURIPOTI: VIEWER na WORKER wanayo. Kuitumia kwa kuandika
--     kungemruhusu kila mwenye macho kwenye ripoti kuhariri katalogi
--     inayosomwa na MASHAMBA YOTE.
--   * `manage_feed_stock` - ndiyo inayolinda createFeedType, na muundo ni
--     ule ule (katalogi ya kimfumo). Lakini maana yake ni "kununua chakula
--     na kusimamia stoo"; aina ya SAMAKI si chakula, na kuiweka kwenye
--     kundi la FEED kungeificha mahali pasipo na uhusiano.
--   * `manage_users` - ni ya module ya UAA. Katalogi ya uzalishaji
--     ingekuwa kitu pekee cha FARM kinacholindwa na ruhusa ya UAA.
--
-- MGAWANYO: OWNER na FARM_MANAGER pekee - ni sawa na `view_feed_cost`
-- (V18). WORKER HAPATI: kuongeza aina si kazi ya shambani, na athari yake
-- inavuka mipaka ya shamba lake. VIEWER HAPATI kwa sababu ile ile
-- inayomzuia kila mahali - hana uwezo wa kubadilisha chochote.
--
-- ATHARI INAYOVUKA MASHAMBA ndiyo hoja kubwa hapa: `species` HAINA
-- farm_id (V1), hivyo aina anayoiandika mtu mmoja inaonekana kwa kila
-- shamba kwenye mfumo. Ndiyo maana ruhusa hii ni ya juu, na ndiyo maana
-- service inatumia `require` pekee - SI `requireFarmScope`: kudai
-- muktadha wa shamba kwa kitu kisicho na shamba ni ile ile sheria ya
-- SpeciesService.listAll na FeedService.listFeedTypes.
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15/V17/V18: inatofautisha DB
-- inayotumika (role tayari zina ruhusa - mistari inaingizwa hapa) na DB
-- mpya kabisa (RbacSeedService itaziweka zote kutoka
-- seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('manage_species', 'FARM', 'PRODUCTION', 'Kuongeza aina za samaki kwenye katalogi')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'manage_species'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER')
ON CONFLICT DO NOTHING;
