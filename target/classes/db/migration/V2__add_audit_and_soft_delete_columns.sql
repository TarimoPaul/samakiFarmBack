-- ============================================================
-- BaseEntity: audit (created_at/updated_at) + soft-delete
-- (deleted_at/is_deleted/deleted_by) kwa entities zenye JPA
-- mapping (angalia com.samaki.farm.domain.BaseEntity).
--
-- users/farms tayari zina created_at (V1) - hazigusiwi hapa.
-- roles/permissions/cycles/daily_tasks/production_units/species
-- hazikuwa na created_at kabisa - zinaongezwa hapa kwa mara ya kwanza.
-- ============================================================

ALTER TABLE users
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE farms
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE roles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE permissions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE cycles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE daily_tasks
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE production_units
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);

ALTER TABLE species
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_by UUID REFERENCES users(user_id),
    ADD COLUMN deleted_by UUID REFERENCES users(user_id);
