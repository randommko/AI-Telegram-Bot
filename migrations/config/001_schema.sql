-- Схема config: персонажи, справочники, настройки бота.
-- Общая схема shared (tg_users, tg_chats) создаётся внешними скриптами migrations-ops.
-- Идемпотентно: IF NOT EXISTS, ON CONFLICT DO NOTHING.
SET client_encoding TO 'UTF8';

CREATE SCHEMA IF NOT EXISTS config;

-- Настройки бота: читаются на каждый запрос, поэтому меняются без перезапуска.
CREATE TABLE IF NOT EXISTS config.bot_settings (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key character varying NOT NULL,
    value text NOT NULL,
    description character varying,
    created_at time with time zone NOT NULL DEFAULT now(),
    CONSTRAINT bot_settings_unique UNIQUE (setting_key)
);

-- Характер персоны и настройки эмоциональности.
CREATE TABLE IF NOT EXISTS config.personas (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name character varying(110) NOT NULL,
    prompt text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    aggression_threshold smallint NOT NULL DEFAULT 8,
    hysteria_replies smallint NOT NULL DEFAULT 4,
    hysteria_cooldown_hours smallint NOT NULL DEFAULT 6,
    max_replies smallint NOT NULL DEFAULT 4,
    reply_delay_ms integer NOT NULL DEFAULT 1500,
    initiative_enabled boolean NOT NULL DEFAULT true,
    initiative_cooldown_messages integer NOT NULL DEFAULT 20,
    last_hysteria_at timestamp with time zone,
    CONSTRAINT personas_name_unique UNIQUE (name)
);

-- Имена, по которым персона откликается. Уникальность — в пределах персоны
-- и без учёта регистра: две персоны в разных чатах вправе зваться одинаково.
CREATE TABLE IF NOT EXISTS config.persona_aliases (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES config.personas(id) ON DELETE CASCADE,
    alias character varying(64) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS persona_aliases_persona_alias_idx
    ON config.persona_aliases (persona_id, lower(alias));

-- Одна персона на чат; отсутствие строки = чат без персоны.
CREATE TABLE IF NOT EXISTS config.chat_personas (
    chat_id bigint PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES config.personas(id) ON DELETE RESTRICT,
    assigned_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Справочник категорий: новые добавляются без миграции.
CREATE TABLE IF NOT EXISTS config.memory_categories (
    code character varying(32) PRIMARY KEY,
    title character varying(64) NOT NULL,
    sort_order integer NOT NULL DEFAULT 100
);

-- Справочник эмоций. is_derived = true только у агрессии:
-- её значение вычисляется из остальных, строки в persona_emotions у неё нет.
CREATE TABLE IF NOT EXISTS config.emotions (
    code character varying(32) PRIMARY KEY,
    title character varying(64) NOT NULL,
    description text NOT NULL DEFAULT '',
    polarity character varying(16) NOT NULL DEFAULT 'нейтральная'
        CHECK (polarity IN ('плюс', 'минус', 'стиль', 'нейтральная')),
    aggression_weight numeric(4, 2) NOT NULL DEFAULT 0
        CHECK (aggression_weight BETWEEN -1 AND 1),
    decay_per_hour numeric(5, 2) NOT NULL DEFAULT 1 CHECK (decay_per_hour >= 0),
    base_default smallint NOT NULL DEFAULT 0 CHECK (base_default BETWEEN 0 AND 10),
    is_derived boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 100
);

-- Фразы «как отвечать» по полосам значения: 0-1, 2-4, 5-6, 7-8, 9-10.
CREATE TABLE IF NOT EXISTS config.emotion_phrases (
    emotion_code character varying(32) NOT NULL
        REFERENCES config.emotions(code) ON DELETE CASCADE,
    from_value smallint NOT NULL CHECK (from_value BETWEEN 0 AND 10),
    to_value smallint NOT NULL CHECK (to_value BETWEEN 0 AND 10),
    phrase text NOT NULL,
    PRIMARY KEY (emotion_code, from_value),
    CONSTRAINT emotion_phrases_range CHECK (from_value <= to_value)
);
