-- ============================================================
-- B1: Kutengua V4 - `users` (mtu) na `farm_users` (uanachama) zinatenganishwa
-- tena, safari hii zikiruhusu UANACHAMA MWINGI kwa mtu mmoja.
--
-- Kwa nini kurudi nyuma: V4 iliunganisha kwa dhana ya "mtumiaji = shamba
-- moja". Uamuzi wa mradi (Part A #3) ni kwamba kampuni inaweza kuendesha
-- mashamba zaidi ya moja, na mtu anaweza kuwa kwenye zaidi ya moja. Role
-- inaishi kwenye UANACHAMA, si kwa mtu.
--
-- V4 HAIHARIWI (ilishatumika) - hii ni migration ya mbele inayoitengua.
-- ============================================================

-- ---------- 1. Rudisha jedwali la mtu kuwa `users` ----------
-- RENAME inarudisha FK zote 23 kwa `users` zenyewe (PostgreSQL inashikilia
-- jedwali kwa OID). MUHIMU: FK hizo zote (recorded_by_user_id,
-- completed_by_user_id, owner_user_id, updated_by, deleted_by, n.k.)
-- zinamaanisha MTU, si uanachama - hivyo kuelekea `users` ndiko sahihi.
-- Hakuna hata moja inayohitaji muktadha wa shamba.
ALTER TABLE farm_users RENAME TO users;

ALTER TABLE users RENAME CONSTRAINT farm_users_pkey TO users_pkey;
ALTER TABLE users RENAME CONSTRAINT farm_users_phone_key TO users_phone_key;
ALTER TABLE users RENAME CONSTRAINT farm_users_email_key TO users_email_key;

-- Index ya V4 imefuata rename na sasa iko kwenye `users`. Majina ya index ni
-- ya kipekee kwenye schema NZIMA, hivyo lazima iondoke kabla jina lile lile
-- halijatumika tena kwenye jedwali jipya la uanachama hapa chini.
DROP INDEX idx_farm_users_farm;

-- ---------- 2. Jedwali jipya la uanachama ----------
-- PK (user_id, farm_id): mtu anaweza kuwa kwenye mashamba mengi, lakini ana
-- role MOJA kwa kila shamba. Hii ndiyo inayoruhusu multi-farm bila utata.
CREATE TABLE farm_users (
    user_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    farm_id    INT  NOT NULL REFERENCES farms(farm_id) ON DELETE CASCADE,
    role_id    INT  REFERENCES roles(role_id),

    -- Audit kamili (tofauti na farm_users ya V1 iliyokuwa haina): kujua NANI
    -- alimpa mtu role gani na LINI ni muhimu kwenye mfumo wenye idhini.
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id),

    PRIMARY KEY (user_id, farm_id)
);

CREATE INDEX idx_farm_users_farm ON farm_users(farm_id);

-- ---------- 3. Hamisha uanachama uliopo kutoka safu za mtu ----------
INSERT INTO farm_users (user_id, farm_id, role_id)
SELECT u.user_id, u.farm_id, u.role_id
FROM users u
WHERE u.farm_id IS NOT NULL;

-- ---------- 4. Ondoa farm/role kwa mtu ----------
-- Index ya zamani (idx_farm_users_farm ya V4, sasa iko kwenye `users`)
-- inaondoka yenyewe pamoja na safu ya farm_id.
ALTER TABLE users
    DROP COLUMN farm_id,
    DROP COLUMN role_id;
