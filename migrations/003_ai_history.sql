-- История AI-переписки и состояние чата: переживают перезапуск процесса (issue #43).
-- Идемпотентная миграция: безопасно накатывать повторно (CREATE ... IF NOT EXISTS, ON CONFLICT DO NOTHING).
-- Накатывать от владельца схемы (admin): таблицы создаются под ним, а в конце
-- миграция выдаёт права роли бота app_user — без этого бот получает permission denied.
SET client_encoding TO 'UTF8';

-- Сообщения AI-переписки. Плоский лог чата: автор уже в префиксе content,
-- поэтому user_id не нужен, а AI-строки не имеют реального пользователя в tg_users.
CREATE TABLE IF NOT EXISTS public.ai_messages (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    role character varying(16) NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Порядок чтения: последние N сообщений одного чата по монотонному id.
CREATE INDEX IF NOT EXISTS ai_messages_chat_id_idx ON public.ai_messages (chat_id, id);

-- Состояние памяти AI по чату: сколько раз её обнуляли.
CREATE TABLE IF NOT EXISTS public.ai_chat_state (
    chat_id bigint PRIMARY KEY,
    resets integer NOT NULL DEFAULT 0
);

-- Отметка обновления чата: заменяет in-memory Map «обновляли сегодня».
ALTER TABLE public.tg_chats
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

-- Лимит истории AI в сообщениях на чат.
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES ('aiHistorySize', '100', 'Сколько последних сообщений чата помнит AI')
ON CONFLICT (setting_key) DO NOTHING;

-- Права роли бота на схему public: CREATE TABLE не наследует права других таблиц,
-- а ALTER DEFAULT PRIVILEGES закрывает вопрос и для таблиц, созданных позже.
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
