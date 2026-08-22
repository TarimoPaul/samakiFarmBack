-- ============================================================
-- B2: `status` inakuwa mzunguko wa maisha wa akaunti, si metadata iliyokufa.
--
--   status      = hali ya akaunti  (PENDING_APPROVAL / ACTIVE / DISABLED)
--   is_deleted  = rekodi imefutwa  (soft-delete, HAIHUSIANI na status)
--
-- Vinatofautiana kwa makusudi: mtu aliyefutwa hatoki kabisa kwenye query
-- (@SQLRestriction), wakati aliye DISABLED bado yupo - anaonekana kwenye
-- orodha za wasimamizi na anaweza kurudishwa ACTIVE.
-- ============================================================

-- Default mpya ni PENDING_APPROVAL: kujisajili HAKUTOI ufikiaji. Rekodi
-- zilizopo zinabaki ACTIVE (zilitengenezwa chini ya utaratibu wa zamani wa
-- signup ya papo hapo).
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'PENDING_APPROVAL';

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'DISABLED'));

-- B8: kulazimisha kubadilisha password mara ya kwanza (hasa kwa ROOT
-- anayetengenezwa kutoka environment variable).
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;

-- Wasimamizi watachuja kwa hali mara kwa mara ("nionyeshe wanaosubiri idhini")
CREATE INDEX idx_users_status ON users(status);
