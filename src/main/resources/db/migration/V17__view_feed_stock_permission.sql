-- ============================================================
-- Ruhusa mpya: kuona stoo ya chakula (`view_feed_stock`).
--
-- KWA NINI SI `view_dashboard` iliyokuwepo. view_dashboard ni ruhusa ya
-- KURIPOTI - kila mtu anayeruhusiwa kuona chochote anayo, ikiwemo VIEWER.
-- Maswali mawili yamehama kutoka kwake:
--
--   * feedStockBalance   - kiasi na thamani ya chakula kilichopo stoo
--   * feedTypesForCycle  - chakula gani kinafaa kwa mzunguko huu
--
-- La kwanza ni hesabu ya mali iliyopo ghalani; la pili ni maelekezo ya
-- kazi ya shambani. Yote mawili ni ya wanaoshika chakula - si ya kila
-- mwenye macho kwenye ripoti. Hivyo OWNER, FARM_MANAGER na WORKER
-- wanaipata (WORKER ndiye anayelisha), VIEWER haipati.
--
-- `feedPurchases` INABAKI kwenye view_dashboard kwa makusudi: ni rekodi ya
-- matumizi ya fedha, ambayo ndiyo kazi ya ripoti.
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15: inatofautisha DB inayotumika
-- (role tayari zina ruhusa - mistari inaingizwa hapa) na DB mpya kabisa
-- (RbacSeedService itaziweka zote kutoka seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('view_feed_stock', 'FARM', 'FEED', 'Kuona salio la stoo na chakula kinachofaa')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'view_feed_stock'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER', 'WORKER')
ON CONFLICT DO NOTHING;
