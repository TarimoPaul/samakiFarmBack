-- ============================================================
-- Forgot-password kwa SMS OTP (angalia com.samaki.farm.security.
-- PasswordResetService) - msimbo unahifadhiwa kama hash pekee (code_hash),
-- kamwe wazi, kama vile users.password_hash.
-- ============================================================

CREATE TABLE password_reset_otps (
    otp_id      BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    is_deleted  BOOLEAN NOT NULL DEFAULT false,
    updated_by  UUID REFERENCES users(user_id),
    deleted_by  UUID REFERENCES users(user_id)
);

-- Query kuu ya PasswordResetOtpRepository (OTP ya hivi karibuni isiyotumika
-- ya user fulani) ndiyo inayotumia index hii.
CREATE INDEX idx_password_reset_otps_user_created ON password_reset_otps(user_id, created_at DESC);
