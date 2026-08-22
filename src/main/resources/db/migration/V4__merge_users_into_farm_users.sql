-- ============================================================
-- Kuunganisha `users` na `farm_users` kuwa jedwali/entity MOJA:
-- com.samaki.farm.domain.FarmUser.
--
-- Kwa nini `users` ndilo linalobaki (likibadilishwa jina) badala ya
-- kufutwa: linarejelewa na FK 14 - farms.owner_user_id,
-- feeding_logs/water_quality_logs.recorded_by_user_id,
-- task_completions.completed_by_user_id, password_reset_otps.user_id,
-- pamoja na updated_by/deleted_by za kila jedwali lenye audit columns.
-- Kwenye PostgreSQL, FK zinashikilia jedwali kwa OID (si kwa jina),
-- hivyo RENAME haivunji hata moja kati yao - hakuna haja ya kufuta na
-- kuunda upya FK yoyote hapa.
--
-- ATHARI: mtumiaji mmoja sasa ana shamba MOJA na role MOJA. Uwezo wa
-- multi-farm (uliokuwa unaruhusiwa na muundo wa join-table) umeondolewa
-- kwa makusudi - code yote ilikuwa inatumia scopes.get(0) pekee, hivyo
-- hakuna tabia inayobadilika kivitendo.
-- ============================================================

-- 1. Nafasi ya farm/role ndani ya `users` yenyewe.
--    Zote mbili ni NULLable: ROOT (is_root = true) hana shamba wala role.
--    ON DELETE SET NULL (si CASCADE kama ilivyokuwa kwenye join-table):
--    kufuta shamba sasa hakumfuti mtu - kunamwacha bila shamba tu.
ALTER TABLE users
    ADD COLUMN farm_id INT REFERENCES farms(farm_id) ON DELETE SET NULL,
    ADD COLUMN role_id INT REFERENCES roles(role_id);

-- 2. Hamisha uanachama uliopo kutoka join-table kwenda ndani ya safu mpya.
--    DISTINCT ON inachukua shamba lenye farm_id ndogo zaidi pale mtumiaji
--    alipokuwa na zaidi ya moja - ndilo lile lile ambalo code ya zamani
--    (scopes.get(0), ikiwa haijapangwa) ingelichagua.
UPDATE users u
SET farm_id = s.farm_id,
    role_id = s.role_id
FROM (
    SELECT DISTINCT ON (user_id) user_id, farm_id, role_id
    FROM farm_users
    ORDER BY user_id, farm_id
) s
WHERE s.user_id = u.user_id;

-- 3. Join-table haihitajiki tena. LAZIMA ifutwe KABLA ya rename ya hatua
--    ya 4 - majedwali mawili hayawezi kubeba jina moja.
DROP TABLE farm_users;

-- 4. users -> farm_users. FK zote 14 zinafuata kiotomatiki.
ALTER TABLE users RENAME TO farm_users;

-- 5. RENAME ya jedwali hairenamishi constraints zake (PostgreSQL) - hizi
--    zingebaki users_pkey/users_phone_key n.k. Kuzisawazisha ni kwa uwazi
--    tu wakati wa ku-debug makosa ya constraint baadaye.
ALTER TABLE farm_users RENAME CONSTRAINT users_pkey TO farm_users_pkey;
ALTER TABLE farm_users RENAME CONSTRAINT users_phone_key TO farm_users_phone_key;
ALTER TABLE farm_users RENAME CONSTRAINT users_email_key TO farm_users_email_key;

-- 6. Query mpya "nipe watumiaji wote wa shamba hili"
--    (FarmUserRepository.findByFarm_FarmId) - bila index hii ni seq scan.
CREATE INDEX idx_farm_users_farm ON farm_users(farm_id);
