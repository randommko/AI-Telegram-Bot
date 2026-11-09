-- Реакции бота: Telegram-идентификатор сообщения, журнал поставленных реакций
-- и отметка, докуда чат прочитан проходом чтения.
SET client_encoding TO 'UTF8';

-- Идентификатор Telegram нужен, чтобы поставить реакцию на конкретное сообщение.
-- У старых записей и у служебных сообщений его нет, поэтому уникальность
-- проверяется только там, где значение заполнено.
ALTER TABLE ai.ai_messages
    ADD COLUMN IF NOT EXISTS tg_message_id bigint;

CREATE UNIQUE INDEX IF NOT EXISTS ai_messages_chat_tg_idx
    ON ai.ai_messages (chat_id, tg_message_id)
    WHERE tg_message_id IS NOT NULL;

-- Журнал поставленных реакций: что поставили, на какое сообщение и почему.
CREATE TABLE IF NOT EXISTS ai.message_reactions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    tg_message_id bigint NOT NULL,
    emoji text NOT NULL,
    reason text,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS message_reactions_chat_idx
    ON ai.message_reactions (chat_id, created_at DESC);

-- Отметка прохода чтения: id последнего сообщения, которое уже оценили.
-- При первом включении ставится на текущий хвост, чтобы история не получила
-- реакции задним числом.
CREATE TABLE IF NOT EXISTS ai.read_state (
    chat_id bigint PRIMARY KEY,
    last_message_id bigint NOT NULL DEFAULT 0,
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
