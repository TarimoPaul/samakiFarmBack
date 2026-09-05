-- ============================================================
-- Ruhusa mpya: kufuta shamba.
--
-- KWA NINI SI `manage_farms` iliyopo. Kila module nyingine inaweka
-- kuunda/kuhariri/kufuta chini ya ruhusa moja - `manage_users` inashughulikia
-- maisha yote ya mtu, ikiwemo kumfuta. Shamba ni tofauti kwa jambo moja la
-- msingi: kulifuta kunafuta MUKTADHA wa kila kitu kilichomo - vitengo,
-- mizunguko, ulishaji, vipimo vya maji - na kila mtu aliyekuwa akifanya kazi
-- humo. Ni kitendo cha nadra chenye athari kubwa kuliko kingine chochote
-- kwenye mfumo huu.
--
-- Kwa hiyo mtu anaweza kupewa uwezo wa KUPANGA mashamba (kuunda, kuorodhesha,
-- kuhariri jina) bila kupewa uwezo wa kuyafuta. Bila mgawanyo huu, kila
-- msimamizi wa mashamba angelazimika kuwa na uwezo huo pia.
--
-- Sheria ni ile ile ya V7/V10: EXISTS inatofautisha DB inayotumika (role
-- tayari zina ruhusa - mstari unaingizwa hapa) na DB mpya (RbacSeedService
-- itaziweka zote kutoka CSV).
-- ============================================================

INSERT INTO permissions (code, module, group_name, description)
SELECT v.code, v.module, v.group_name, v.description
FROM (VALUES
    ('delete_farm', 'FARM', 'FARM_MANAGEMENT', 'Kufuta shamba')
) AS v(code, module, group_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'delete_farm'
WHERE EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.role_id)
  AND r.name = 'OWNER'
ON CONFLICT DO NOTHING;
