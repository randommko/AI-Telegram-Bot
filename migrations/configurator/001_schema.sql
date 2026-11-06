-- Схема configurator: учётки админки и выданные чаты.
-- Журнала изменений нет: он удалён вместе с историей и откатами.
SET client_encoding TO 'UTF8';

CREATE SCHEMA IF NOT EXISTS configurator;

-- Учётные записи модераторов. Человек берётся из shared.tg_users.
CREATE TABLE IF NOT EXISTS configurator.admin_accounts (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tg_user_id bigint NOT NULL UNIQUE REFERENCES shared.tg_users(user_id) ON DELETE CASCADE,
    login character varying(64) NOT NULL UNIQUE,
    password_hash character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    last_login_at timestamp with time zone,
    CONSTRAINT admin_accounts_login_not_admin CHECK (lower(login) <> 'admin')
);

-- Выданные чаты: единственный источник прав модератора.
CREATE TABLE IF NOT EXISTS configurator.chat_moderators (
    chat_id bigint NOT NULL REFERENCES shared.tg_chats(chat_id) ON DELETE CASCADE,
    tg_user_id bigint NOT NULL REFERENCES shared.tg_users(user_id) ON DELETE CASCADE,
    granted_by bigint,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, tg_user_id)
);

CREATE INDEX IF NOT EXISTS chat_moderators_user_idx
    ON configurator.chat_moderators (tg_user_id);

-- Персональный доступ к персонам: чат даёт только просмотр, правку выдаёт владелец.
CREATE TABLE IF NOT EXISTS configurator.moderator_personas (
    tg_user_id bigint NOT NULL REFERENCES shared.tg_users(user_id) ON DELETE CASCADE,
    persona_id integer NOT NULL REFERENCES config.personas(id) ON DELETE CASCADE,
    granted_by bigint,
    granted_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (tg_user_id, persona_id)
);

-- Прежнее правило разрешало модератору править персону, все чаты которой ему выданы.
-- Чтобы после разделения прав никто не потерял доступ, такие пары становятся явными.
INSERT INTO configurator.moderator_personas (tg_user_id, persona_id, granted_by)
SELECT owned.tg_user_id, owned.persona_id, NULL
FROM (
    SELECT cm.tg_user_id, cp.persona_id
    FROM configurator.chat_moderators cm
    JOIN config.chat_personas cp ON cp.chat_id = cm.chat_id
    GROUP BY cm.tg_user_id, cp.persona_id
) owned
WHERE NOT EXISTS (
    SELECT 1
    FROM config.chat_personas all_cp
    LEFT JOIN configurator.chat_moderators own
        ON own.chat_id = all_cp.chat_id AND own.tg_user_id = owned.tg_user_id
    WHERE all_cp.persona_id = owned.persona_id AND own.chat_id IS NULL
)
ON CONFLICT DO NOTHING;
