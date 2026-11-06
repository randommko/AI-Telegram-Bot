-- Общая схема shared: пользователи и чаты Telegram.
-- Нужна AI-боту, админке и Fun-боту. Каждый репозиторий держит свою копию,
-- чтобы раскатка базы не зависела от соседнего проекта.
-- Накат идемпотентен: безопасно применять повторно.
SET client_encoding TO 'UTF8';

CREATE SCHEMA IF NOT EXISTS shared;

CREATE TABLE IF NOT EXISTS shared.tg_users (
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

CREATE TABLE IF NOT EXISTS shared.tg_chats (
    chat_id bigint PRIMARY KEY,
    chat_title character varying,
    created_at timestamp without time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);

ALTER TABLE shared.tg_users
    ADD COLUMN IF NOT EXISTS updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE shared.tg_chats
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();
