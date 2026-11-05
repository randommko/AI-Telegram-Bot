-- Схема ai: история переписки, память, эмоции, обиды и статус ключей.
-- Зависит от config (персоны, эмоции) и shared (пользователи): их создают
-- migrations/config/001 и внешние скрипты migrations-ops.
SET client_encoding TO 'UTF8';

CREATE SCHEMA IF NOT EXISTS ai;

-- Сообщения AI-переписки. Автор хранится полями, а не префиксом в тексте.
CREATE TABLE IF NOT EXISTS ai.ai_messages (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    role character varying(16) NOT NULL,
    content text NOT NULL,
    author_user_id bigint,
    author_nick character varying(64),
    author_name character varying(128),
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ai_messages_chat_id_idx ON ai.ai_messages (chat_id, id);

CREATE INDEX IF NOT EXISTS ai_messages_content_fts_idx
    ON ai.ai_messages USING GIN (to_tsvector('russian', content));

-- Состояние памяти AI по чату: сколько раз её обнуляли.
CREATE TABLE IF NOT EXISTS ai.ai_chat_state (
    chat_id bigint PRIMARY KEY,
    resets integer NOT NULL DEFAULT 0
);

-- Воспоминания персоны. is_core — незыблемый факт: создаётся только вручную,
-- не вытесняется лимитом топ-N и не удаляется автоматическим анализом.
-- deleted_at — мягкое удаление: запись возвращается из списка «удалённые».
CREATE TABLE IF NOT EXISTS ai.persona_memories (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES config.personas(id) ON DELETE CASCADE,
    category character varying(32) NOT NULL DEFAULT 'другое'
        REFERENCES config.memory_categories(code) ON UPDATE CASCADE,
    content text NOT NULL,
    importance smallint NOT NULL DEFAULT 5 CHECK (importance BETWEEN 1 AND 10),
    is_core boolean NOT NULL DEFAULT false,
    event_date date,
    source character varying(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('auto', 'manual')),
    deleted_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS persona_memories_top_idx
    ON ai.persona_memories (persona_id, importance DESC, event_date DESC NULLS LAST, id DESC);

CREATE INDEX IF NOT EXISTS persona_memories_core_idx
    ON ai.persona_memories (persona_id) WHERE is_core;

CREATE INDEX IF NOT EXISTS persona_memories_alive_idx
    ON ai.persona_memories (persona_id) WHERE deleted_at IS NULL;

-- Связь воспоминания с людьми чата по Telegram user_id.
CREATE TABLE IF NOT EXISTS ai.memory_people (
    memory_id integer NOT NULL REFERENCES ai.persona_memories(id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES shared.tg_users(user_id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, user_id)
);

-- Отметка разобранных сообщений: рестарт не теряет прогресс и не дублирует разбор.
CREATE TABLE IF NOT EXISTS ai.memory_analysis_state (
    chat_id bigint PRIMARY KEY,
    last_message_id bigint NOT NULL DEFAULT 0,
    analyzed_at timestamp with time zone
);

-- Характер (base_value) и текущее настроение (value) персоны по каждой эмоции.
CREATE TABLE IF NOT EXISTS ai.persona_emotions (
    persona_id integer NOT NULL REFERENCES config.personas(id) ON DELETE CASCADE,
    emotion_code character varying(32) NOT NULL
        REFERENCES config.emotions(code) ON DELETE CASCADE,
    base_value smallint NOT NULL CHECK (base_value BETWEEN 0 AND 10),
    value smallint NOT NULL CHECK (value BETWEEN 0 AND 10),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (persona_id, emotion_code)
);

-- Обиды на людей: одна запись на человека с накопленной силой.
CREATE TABLE IF NOT EXISTS ai.persona_grudges (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    user_id bigint NOT NULL REFERENCES shared.tg_users(user_id) ON DELETE CASCADE,
    strength smallint NOT NULL DEFAULT 0 CHECK (strength BETWEEN 0 AND 10),
    last_offence text,
    source_message_id bigint,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT persona_grudges_chat_user_unique UNIQUE (chat_id, user_id)
);

-- Подсказки к полям карточки персоны.
CREATE TABLE IF NOT EXISTS ai.persona_field_notes (
    field_key character varying(64) PRIMARY KEY,
    note text NOT NULL
);

-- Признак наличия ключа провайдера. Сами ключи остаются в env AI-бота.
CREATE TABLE IF NOT EXISTS ai.provider_key_status (
    provider_id character varying(64) PRIMARY KEY,
    has_key boolean NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
