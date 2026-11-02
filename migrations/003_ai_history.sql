-- История AI-переписки и состояние чата: переживают перезапуск процесса (issue #43).
-- Идемпотентная миграция: безопасно накатывать повторно (CREATE ... IF NOT EXISTS, ON CONFLICT DO NOTHING).
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
