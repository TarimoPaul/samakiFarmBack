-- ============================================================
-- Ruhusa mpya: daftari la mali (`manage_assets`).
--
-- INAFUNGA LANGO LA MODULE NZIMA - kusoma NA kuandika. Tofauti na
-- `species` au `feed_types`, ambapo kusoma ni `view_dashboard` (kila
-- anayechagua aina wakati wa kazi anahitaji orodha), hakuna kazi ya
-- shambani inayohitaji kujua bei ya generator. Daftari la mali ni jibu
-- la swali la KIMTAJI: "kampuni imewekeza kiasi gani, na wapi?".
--
-- HAKUNA RUHUSA ILIYOKUWEPO INAYOFAA:
--
--   * `view_finance` - ipo, na ni ya REPORTING. Lakini haijawahi
--     kutumiwa na msimbo wowote (hakuna module ya fedha bado), na
--     kuianzisha hapa kungeifunga maana yake kwenye mali kabla
--     gharama/mauzo hazijafika. Mali si gharama ya mzunguko.
--   * `view_dashboard` - VIEWER na WORKER wanayo. Ingefanya bei ya kila
--     kitu kampuni inachomiliki ionekane kwa kila mwenye macho kwenye
--     ripoti.
--   * `manage_farms` - ni ruhusa ya KAMPANI (angalia PermissionChecker.
--     COMPANY_WIDE_PERMISSION), na daftari hili ni la kampuni nzima,
--     hivyo ilikuwa mgombea wa karibu zaidi. Lakini maana yake ni
--     "kuunda na kuorodhesha mashamba" - usimamizi wa muundo, si wa
--     mali zilizo ndani yake. FARM_MANAGER HANA ruhusa hiyo kwa
--     makusudi, na ndiye hasa anayepaswa kuandikisha mali za shamba
--     lake.
--
-- MGAWANYO: OWNER na FARM_MANAGER pekee - sawa na `view_feed_cost`
-- (V18) na `manage_species` (V20). WORKER HAPATI: kuandikisha mali si
-- kazi ya kila siku ya shambani, na jibu lake linavuka mipaka ya shamba
-- lake. VIEWER HAPATI kwa sababu ile ile inayomzuia kila mahali.
--
-- MUHIMU - KWA NINI SI `requireFarmScope`. Query ya `assets` inarudisha
-- mali za MASHAMBA YOTE ambayo mwombaji ni mwanachama wake, si ya
-- shamba moja teule. Ndiyo maana AssetService inatumia `require` pekee
-- kisha inakokotoa mashamba yake kutoka `farm_users` (angalia
-- AssetService.callersFarmIds). Ukaguzi wa uanachama upo pale, si hapa:
-- ruhusa inasema "unaruhusiwa kuona daftari", uanachama unasema
-- "daftari LIPI".
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15/V17/V18/V20: inatofautisha
-- DB inayotumika (role tayari zina ruhusa - mistari inaingizwa hapa) na
-- DB mpya kabisa (RbacSeedService itaziweka zote kutoka
-- seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('manage_assets', 'FARM', 'ASSETS', 'Kuona na kuandikisha mali za kampuni')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'manage_assets'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER')
ON CONFLICT DO NOTHING;
