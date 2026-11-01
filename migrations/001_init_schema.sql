-- Инициализация схемы Telegram-бота.
-- Идемпотентная миграция: безопасно накатывать повторно (CREATE TABLE IF NOT EXISTS).
SET client_encoding TO 'UTF8';

-- Пользователи Telegram
CREATE TABLE IF NOT EXISTS public.tg_users (
    user_id bigint PRIMARY KEY,
    first_name character varying,
    last_name character varying,
    user_name character varying,
    is_bot boolean,
    language_code character varying,
    can_join_groups boolean,
    can_read_all_group_messages boolean,
    support_inline_queries boolean,
    is_premium boolean,
    added_to_attachment_menu boolean,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

-- Чаты Telegram
CREATE TABLE IF NOT EXISTS public.tg_chats (
    chat_id bigint PRIMARY KEY,
    chat_title character varying,
    created_at timestamp without time zone NOT NULL DEFAULT now()
);

-- Фразы игры «Пидор дня»
CREATE TABLE IF NOT EXISTS public.pidor_messages (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text text NOT NULL,
    group_num integer NOT NULL,
    created_at date NOT NULL DEFAULT now()
);

-- Настройки бота
CREATE TABLE IF NOT EXISTS public.bot_settings (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key character varying NOT NULL,
    value text NOT NULL,
    description character varying,
    created_at time with time zone NOT NULL DEFAULT now(),
    CONSTRAINT bot_settings_unique UNIQUE (setting_key)
);

-- Картинки для «замера достоинства»
CREATE TABLE IF NOT EXISTS public.cocksize_imgs (
    cock_size integer PRIMARY KEY,
    img text,
    is_using boolean NOT NULL DEFAULT true
);

-- Замеры «достоинства»
CREATE TABLE IF NOT EXISTS public.cocksize_stats (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES public.tg_users(user_id),
    size integer NOT NULL,
    date date NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);

-- Игроки «Пидора дня»
CREATE TABLE IF NOT EXISTS public.pidor_players (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    user_id bigint NOT NULL REFERENCES public.tg_users(user_id),
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT pidor_players_un UNIQUE (user_id, chat_id)
);

-- Статистика «Пидора дня»
CREATE TABLE IF NOT EXISTS public.pidor_stats (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id bigint NOT NULL,
    date date NOT NULL,
    user_id bigint NOT NULL REFERENCES public.tg_users(user_id),
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT pidor_stats_un UNIQUE (chat_id, date, user_id)
);
