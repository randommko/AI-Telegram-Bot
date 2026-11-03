-- Доступ модераторов в админку (issue #76, change moderator-access).
-- Идемпотентная миграция: безопасно накатывать повторно.
-- Накатывать от владельца схемы (admin): в конце выдаются права роли бота app_user.
SET client_encoding TO 'UTF8';

-- Учётные записи модераторов. Человек берётся из tg_users, поэтому ник и имя
-- для сообщений о выходе всегда доступны без второй копии.
CREATE TABLE IF NOT EXISTS public.admin_accounts (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tg_user_id bigint NOT NULL UNIQUE REFERENCES public.tg_users(user_id) ON DELETE CASCADE,
    login character varying(64) NOT NULL UNIQUE,
    password_hash character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    last_login_at timestamp with time zone,
    -- Логин admin обслуживается паролем из окружения; строка с таким логином
    -- перекрыла бы владельца неявно, поэтому запрещена и в схеме.
    CONSTRAINT admin_accounts_login_not_admin CHECK (lower(login) <> 'admin')
);

-- Выданные чаты: единственный источник прав модератора.
CREATE TABLE IF NOT EXISTS public.chat_moderators (
    chat_id bigint NOT NULL REFERENCES public.tg_chats(chat_id) ON DELETE CASCADE,
    tg_user_id bigint NOT NULL REFERENCES public.tg_users(user_id) ON DELETE CASCADE,
    granted_by bigint,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (chat_id, tg_user_id)
);

CREATE INDEX IF NOT EXISTS chat_moderators_user_idx
    ON public.chat_moderators (tg_user_id);

-- Журнал изменений админки: старые и новые значения только изменённых полей.
-- actor_login хранится текстом, чтобы журнал пережил удаление учётки.
CREATE TABLE IF NOT EXISTS public.admin_changes (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at timestamp with time zone NOT NULL DEFAULT now(),
    actor_user_id bigint,
    actor_login character varying(64) NOT NULL,
    entity character varying(32) NOT NULL,
    entity_id integer,
    chat_id bigint,
    persona_id integer,
    before jsonb,
    after jsonb
);

CREATE INDEX IF NOT EXISTS admin_changes_persona_idx
    ON public.admin_changes (persona_id, at DESC, id DESC);

CREATE INDEX IF NOT EXISTS admin_changes_entity_idx
    ON public.admin_changes (entity, entity_id, at DESC);

-- Мягкое удаление воспоминаний: запись исчезает из списка и промпта,
-- но остаётся в таблице и возвращается из журнала.
ALTER TABLE public.persona_memories
    ADD COLUMN IF NOT EXISTS deleted_at timestamp with time zone;

CREATE INDEX IF NOT EXISTS persona_memories_alive_idx
    ON public.persona_memories (persona_id) WHERE deleted_at IS NULL;

-- Сообщение о выходе называет того, кто снял персону. Уже правленый вручную
-- текст не затирается: плейсхолдер дописывается только если его там нет.
UPDATE public.bot_settings
SET value = rtrim(rtrim(value), ' .') || '. Убрал: {moderator}'
WHERE setting_key = 'aiPersonaLeaveTemplate'
  AND value NOT LIKE '%{moderator}%';

-- Права роли бота на новые таблицы и колонку.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO app_user';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_user';
    END IF;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Права для app_user не выданы: нужен запуск от владельца схемы (admin)';
END $$;
