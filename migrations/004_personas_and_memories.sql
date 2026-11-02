-- Персоны и долговременная память (issues #53–#56).
-- Идемпотентная миграция: безопасно накатывать повторно (CREATE ... IF NOT EXISTS,
-- INSERT ... ON CONFLICT DO NOTHING).
-- Накатывать от владельца схемы (admin): таблицы создаются под ним, а в конце
-- миграция выдаёт права роли бота app_user — без этого бот получает permission denied.
--
-- ВНИМАНИЕ: текст промпта персоны в репозиторий не попадает. Персона «Юра»
-- создаётся запросом INSERT ... SELECT из bot_settings.aiPromtMyChatContext.
SET client_encoding TO 'UTF8';

-- Характер персоны и настройки эмоциональности из #51.
CREATE TABLE IF NOT EXISTS public.personas (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name character varying(110) NOT NULL,
    prompt text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    base_joy smallint NOT NULL DEFAULT 5 CHECK (base_joy BETWEEN 0 AND 10),
    base_anger smallint NOT NULL DEFAULT 2 CHECK (base_anger BETWEEN 0 AND 10),
    base_offence smallint NOT NULL DEFAULT 0 CHECK (base_offence BETWEEN 0 AND 10),
    base_fatigue smallint NOT NULL DEFAULT 3 CHECK (base_fatigue BETWEEN 0 AND 10),
    base_sarcasm smallint NOT NULL DEFAULT 5 CHECK (base_sarcasm BETWEEN 0 AND 10),
    offence_decay_per_hour smallint NOT NULL DEFAULT 1,
    anger_threshold smallint NOT NULL DEFAULT 8,
    hysteria_replies smallint NOT NULL DEFAULT 4,
    hysteria_cooldown_hours smallint NOT NULL DEFAULT 6,
    max_replies smallint NOT NULL DEFAULT 4,
    reply_delay_ms integer NOT NULL DEFAULT 1500,
    initiative_enabled boolean NOT NULL DEFAULT true,
    initiative_cooldown_messages integer NOT NULL DEFAULT 20,
    CONSTRAINT personas_name_unique UNIQUE (name)
);

-- Имена, по которым персона откликается. Уникальность — в пределах персоны
-- и без учёта регистра: две персоны в разных чатах вправе зваться одинаково.
CREATE TABLE IF NOT EXISTS public.persona_aliases (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES public.personas(id) ON DELETE CASCADE,
    alias character varying(64) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS persona_aliases_persona_alias_idx
    ON public.persona_aliases (persona_id, lower(alias));

-- Одна персона на чат; отсутствие строки = чат без персоны.
CREATE TABLE IF NOT EXISTS public.chat_personas (
    chat_id bigint PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES public.personas(id) ON DELETE RESTRICT,
    assigned_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Справочник категорий: новые добавляются без миграции.
CREATE TABLE IF NOT EXISTS public.memory_categories (
    code character varying(32) PRIMARY KEY,
    title character varying(64) NOT NULL,
    sort_order integer NOT NULL DEFAULT 100
);

-- Воспоминания персоны. is_core — незыблемый факт: создаётся только вручную,
-- не вытесняется лимитом топ-N и не удаляется автоматическим анализом.
CREATE TABLE IF NOT EXISTS public.persona_memories (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id integer NOT NULL REFERENCES public.personas(id) ON DELETE CASCADE,
    category character varying(32) NOT NULL DEFAULT 'другое'
        REFERENCES public.memory_categories(code) ON UPDATE CASCADE,
    content text NOT NULL,
    importance smallint NOT NULL DEFAULT 5 CHECK (importance BETWEEN 1 AND 10),
    is_core boolean NOT NULL DEFAULT false,
    event_date date,
    source character varying(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('auto', 'manual')),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Выборка топ-N обычных воспоминаний: важность, затем свежесть события.
CREATE INDEX IF NOT EXISTS persona_memories_top_idx
    ON public.persona_memories (persona_id, importance DESC, event_date DESC NULLS LAST, id DESC);

-- Ядро персоны всегда читается целиком.
CREATE INDEX IF NOT EXISTS persona_memories_core_idx
    ON public.persona_memories (persona_id) WHERE is_core;

-- Связь воспоминания с людьми чата по Telegram user_id.
CREATE TABLE IF NOT EXISTS public.memory_people (
    memory_id integer NOT NULL REFERENCES public.persona_memories(id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES public.tg_users(user_id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, user_id)
);

-- Отметка разобранных сообщений: рестарт не теряет прогресс и не дублирует разбор.
CREATE TABLE IF NOT EXISTS public.memory_analysis_state (
    chat_id bigint PRIMARY KEY,
    last_message_id bigint NOT NULL DEFAULT 0,
    analyzed_at timestamp with time zone
);

-- Текущее настроение персоны в чате (#51).
CREATE TABLE IF NOT EXISTS public.persona_state (
    chat_id bigint PRIMARY KEY,
    joy smallint NOT NULL DEFAULT 5 CHECK (joy BETWEEN 0 AND 10),
    anger smallint NOT NULL DEFAULT 2 CHECK (anger BETWEEN 0 AND 10),
    offence smallint NOT NULL DEFAULT 0 CHECK (offence BETWEEN 0 AND 10),
    fatigue smallint NOT NULL DEFAULT 3 CHECK (fatigue BETWEEN 0 AND 10),
    sarcasm smallint NOT NULL DEFAULT 5 CHECK (sarcasm BETWEEN 0 AND 10),
    mood_text text,
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Обиды на людей (#51): одна запись на человека с накопленной силой.
CREATE TABLE IF NOT EXISTS public.persona_grudges (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    user_id bigint NOT NULL REFERENCES public.tg_users(user_id) ON DELETE CASCADE,
    strength smallint NOT NULL DEFAULT 0 CHECK (strength BETWEEN 0 AND 10),
    last_offence text,
    source_message_id bigint,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT persona_grudges_chat_user_unique UNIQUE (chat_id, user_id)
);

-- Полнотекстовый поиск контекста по теме (#51). Таблица ai_messages создаётся
-- миграцией 003, поэтому индекс ставится только если она уже есть.
DO $$
BEGIN
    IF to_regclass('public.ai_messages') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS ai_messages_content_fts_idx
                 ON public.ai_messages USING GIN (to_tsvector(''russian'', content))';
    END IF;
END $$;

-- Справочник категорий памяти.
INSERT INTO public.memory_categories (code, title, sort_order)
VALUES
    ('семья', 'Семья', 10),
    ('друзья', 'Друзья', 20),
    ('работа', 'Работа', 30),
    ('хобби', 'Хобби', 40),
    ('личное', 'Личное', 50),
    ('события', 'События', 60),
    ('другое', 'Другое', 999)
ON CONFLICT (code) DO NOTHING;

-- Настройки памяти и шаблоны сообщений персоны.
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES
    ('aiMemoryPromptSize', '30',
     'Сколько обычных воспоминаний максимум идёт в промпт (ядро в лимит не входит)'),
    ('aiMemoryAnalyzeEvery', '50',
     'Через сколько новых сообщений запускать анализ переписки'),
    ('aiMemoryExtractPrompt',
     'Ты пополняешь долговременную память персоны по переписке чата.
Тебе даны существующие воспоминания в виде id | категория | текст | важность | ядро и новые сообщения.
Найди только устойчивые факты: о персоне, о людях в чате и о событиях. Не запоминай разовые шутки, ругань и мелочи дня.
Верни строго JSON без пояснений и без markdown:
{"add":[{"category":"хобби","content":"играет в шахматы по средам","importance":6,"event_date":null,"people":["@nick"]}],"update":[],"delete":[]}
Правила: category — одна из семья, друзья, работа, хобби, личное, события, другое; importance — целое 1..10, где 10 — важнейший факт жизни; event_date — YYYY-MM-DD или null; people — ники участников из сообщений.
Если факт уже есть в существующих воспоминаниях — используй update с его id. Если факт устарел и стал неверным — delete с его id. Если нового нет — пустые массивы.',
     'Промпт анализа переписки на воспоминания'),
    ('aiPersonaJoinTemplate', 'В чат приходит {name}',
     'Шаблон сообщения о входе персоны в чат'),
    ('aiPersonaLeaveTemplate', '{name} покинул чат',
     'Шаблон сообщения о выходе персоны из чата')
ON CONFLICT (setting_key) DO NOTHING;

-- Персона «Юра»: промпт переносится из настройки, в git не попадает.
INSERT INTO public.personas (name, prompt)
SELECT 'Юра', btrim(value)
FROM public.bot_settings
WHERE setting_key = 'aiPromtMyChatContext' AND btrim(value) <> ''
ON CONFLICT (name) DO NOTHING;

-- Алиасы и привязка к «синему» чату — только если персона действительно создалась.
INSERT INTO public.persona_aliases (persona_id, alias)
SELECT p.id, a.alias
FROM public.personas p
CROSS JOIN (VALUES ('юр'), ('юра'), ('юрец'), ('юрчик'), ('юрий')) AS a(alias)
WHERE p.name = 'Юра'
ON CONFLICT DO NOTHING;

INSERT INTO public.chat_personas (chat_id, persona_id)
SELECT -1001347887839, p.id
FROM public.personas p
WHERE p.name = 'Юра'
ON CONFLICT (chat_id) DO NOTHING;

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
