-- ============================================================
-- Ruhusa mpya: daftari la gharama za uendeshaji (`manage_costs`).
--
-- INAFUNGA LANGO LA MODULE NZIMA - kusoma NA kuandika, kama
-- `manage_assets` (V22). Gharama ni jibu la swali la KIFEDHA
-- ("tumetumia kiasi gani, wapi, kwenye nini?"), si taarifa ya kazi ya
-- leo: hakuna kazi ya shambani inayohitaji kujua bili ya umeme.
--
-- HAKUNA RUHUSA ILIYOKUWEPO INAYOFAA:
--
--   * `manage_assets` (V22) - ndiyo iliyokuwa karibu zaidi: zote mbili
--     ni data ya fedha ya mmiliki/meneja, zote mbili zinavuka mashamba,
--     na wenye ruhusa ni WALE WALE leo. Lakini V22 yenyewe ilikwisha
--     kata swali hili: "Mali si gharama ya mzunguko". Daftari la mali
--     ni la MTAJI (kitu kilichonunuliwa kikabaki), gharama ni za
--     UENDESHAJI (fedha iliyotoka ikaisha). Kuziunganisha kwenye
--     ruhusa moja kungemaanisha kwamba siku mtu anapotakiwa kuandika
--     gharama BILA kuona thamani ya mali za kampuni - au kinyume chake
--     - hakuna njia ya kufanya hivyo bila migration.
--   * `view_finance` - ipo tangu V1, ni ya FINANCE/REPORTING, maelezo
--     yake ni "Kuona gharama/mauzo/faida", na tayari OWNER na
--     FARM_MANAGER wanayo. Ingekuwa rahisi kuitumia hapa, LAKINI ni
--     ruhusa ya KUONA: ikitumika kulinda `createCost` ingebeba mamlaka
--     ya kuandika ambayo jina lake halisemi. Repo hii inatenganisha
--     kusoma na kuandika mahali pengine kote (`view_feed_cost` dhidi ya
--     `manage_feed_stock`, `view_feed_stock` dhidi ya `log_feeding`).
--     INABAKI IKISUBIRI ripoti ya faida - ambayo itasoma gharama NA
--     mauzo NA chakula pamoja, na ndipo maana yake itakapokamilika.
--   * `view_dashboard` - VIEWER na WORKER wanayo. Ingefungua matumizi
--     yote ya fedha ya kampuni kwa kila mwenye macho kwenye ripoti.
--
-- MGAWANYO: OWNER na FARM_MANAGER pekee - sawa na `manage_assets`
-- (V22), `view_feed_cost` (V18) na `manage_species` (V20). FARM_MANAGER
-- ndiye anayeendesha shamba kila siku, hivyo ndiye anayejua bili
-- ilipofika; WORKER na VIEWER hawapati kwa sababu ile ile ya V22.
--
-- MUHIMU - KWA NINI SI `requireFarmScope`. Query ya `costs` inarudisha
-- gharama za MASHAMBA YOTE ambayo mwombaji ni mwanachama wake, si za
-- shamba moja teule - ni daftari la kampuni, kama `assets`. Ndiyo maana
-- CostService inatumia `require` pekee kisha inakokotoa mashamba yake
-- kupitia FarmMembershipService.callersFarmIds. Ruhusa inasema
-- "unaruhusiwa kuona daftari"; uanachama unasema "daftari LIPI".
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15/V17/V18/V20/V22: inatofautisha
-- DB inayotumika (role tayari zina ruhusa - mistari inaingizwa hapa) na
-- DB mpya kabisa (RbacSeedService itaziweka zote kutoka
-- seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('manage_costs', 'FINANCE', 'COSTS', 'Kuona na kurekodi gharama za uendeshaji')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'manage_costs'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER')
ON CONFLICT DO NOTHING;
