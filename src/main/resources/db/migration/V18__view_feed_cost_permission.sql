-- ============================================================
-- Ruhusa mpya: kuona GHARAMA ya chakula (`view_feed_cost`).
--
-- KWA NINI SI `view_dashboard` wala `view_finance`. `feedPurchases`
-- inabaki kwenye view_dashboard (V17 iliieleza hivyo kwa makusudi: rekodi
-- ya manunuzi ni kazi ya ripoti). Lakini mstari mmoja wa manunuzi unabeba
-- vitu VIWILI vya aina tofauti kabisa:
--
--   * kiasi, aina, tarehe, muuzaji - taarifa ya UENDESHAJI. Anayelisha
--     anahitaji kujua magunia mangapi yaliingia na lini.
--   * unitCost / totalCost        - taarifa ya BEI. Ni mkataba wa
--     kibiashara kati ya shamba na muuzaji.
--
-- Hadi sasa vyote vilikuwa vinasafiri pamoja kwa mwenye view_dashboard
-- yeyote - ikiwemo WORKER. Kuficha safu kwenye UI hakutoshi: namba
-- ingekuwa TAYARI IMESHATOKA kwenye waya, ikionekana kwa yeyote
-- anayefungua DevTools au kupiga /graphql moja kwa moja. Hivyo ukaguzi
-- umewekwa kwenye service (FeedService.listPurchases), na ruhusa hii ndiyo
-- inayoufungua.
--
-- `view_finance` HAIKUTUMIKA kwa sababu ni ruhusa ya MODULE nzima ya fedha
-- (mauzo, faida, gharama zote). Meneja anayeruhusiwa kununua chakula na
-- kuona bei ya gunia si lazima aone faida ya shamba - na kinyume chake ni
-- kweli pia. Kuunganisha mawili haya kungefanya kila mnunuzi wa chakula
-- awe mwenye kuona hesabu za kampuni.
--
-- MGAWANYO: OWNER na FARM_MANAGER pekee. WORKER HAPATI - ndiye anayelisha
-- na anayeweza kuwa na `manage_feed_stock`, lakini bei anayolipa mwajiri
-- si taarifa yake ya kazi. VIEWER HAPATI - anaendelea kuona manunuzi kama
-- ripoti, bila namba za bei.
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15/V17: inatofautisha DB
-- inayotumika (role tayari zina ruhusa - mistari inaingizwa hapa) na DB
-- mpya kabisa (RbacSeedService itaziweka zote kutoka
-- seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('view_feed_cost', 'FARM', 'FEED', 'Kuona bei na gharama ya manunuzi ya chakula')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'view_feed_cost'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER')
ON CONFLICT DO NOTHING;
