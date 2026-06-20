ALTER TABLE users
    ALTER COLUMN email TYPE VARCHAR(320);

ALTER TABLE users
    ADD COLUMN email_normalized VARCHAR(320);

UPDATE users
SET email_normalized = lower(trim(email));

DO $$
BEGIN
    IF EXISTS (
        SELECT email_normalized
        FROM users
        GROUP BY email_normalized
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot normalize user emails: duplicate values differing only by case or surrounding whitespace exist';
    END IF;
END
$$;

ALTER TABLE users
    ALTER COLUMN email_normalized SET NOT NULL;

CREATE UNIQUE INDEX uk_users_email_normalized
    ON users (email_normalized);

ALTER TABLE users
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET status = 'ACTIVE',
    email_verified_at = created_at;

ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'PENDING_VERIFICATION';

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DELETED'));
