-- ============================================================
-- Ruhusa mpya: kurekodi matukio ya mavuno (`record_harvest`).
--
-- Inalinda recordHarvestEvent NA deleteHarvestEvent. Kusoma
-- (`harvestEvents`) ni `view_dashboard`, kama `cycles` - lakini
-- sale_amount inarudi null bila `view_finance` (angalia HarvestResolver).
--
-- KWA NINI SI `edit_cycle`. closeCycle inabaki kwenye `edit_cycle` - ile
-- ile ya kuweka, "anayeanzisha ndiye anayemaliza". Lakini matukio ya
-- mavuno ni kazi ya KILA SIKU, si ya mwanzo na mwisho: kuyafunga kwenye
-- `edit_cycle` kungemaanisha kwamba siku mtu anapotakiwa kurekodi vifo
-- vya leo, lazima pia apewe mamlaka ya kuunda na KUFUNGA mizunguko. Ni
-- mgawanyo ule ule wa `log_feeding` dhidi ya `manage_feed_stock`.
--
-- MGAWANYO: OWNER na FARM_MANAGER PEKEE kwa hatua hii ya kwanza. SOLD
-- inabeba FEDHA (sale_amount), na kurekodi mauzo ni kazi ya anayepokea
-- fedha. WORKER HAPATI - bado. Ruhusa ikiwa tofauti, kumpa baadaye (kwa
-- vifo, kwa mfano) ni mstari mmoja wa role_permissions, si migration ya
-- kutenganisha.
--
-- Sheria ya EXISTS ni ile ile ya V7/V8/V15/V17/V18/V20/V22/V24:
-- inatofautisha DB inayotumika (role tayari zina ruhusa - mistari
-- inaingizwa hapa) na DB mpya kabisa (RbacSeedService itaziweka zote
-- kutoka seed/role_permissions.csv).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('record_harvest', 'FARM', 'PRODUCTION', 'Kurekodi mauzo vifo na uhamisho wa samaki')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'record_harvest'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name IN ('OWNER', 'FARM_MANAGER')
ON CONFLICT DO NOTHING;
