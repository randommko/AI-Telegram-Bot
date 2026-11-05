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
