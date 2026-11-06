-- Роли AI-бота и админки и их права.
-- Пароли в репозитории не хранятся: заменить CHANGE_ME_* перед запуском
-- (или создать роли вручную — тогда блок IF NOT EXISTS их не тронет).
-- Накатывать после миграций схем: гранты выдаются на существующие таблицы.
-- Права на будущие таблицы задаёт ALTER DEFAULT PRIVILEGES — они действуют
-- для объектов, которые создаст та роль, под которой выполняется этот файл
-- (обычно app_user или postgres).
SET client_encoding TO 'UTF8';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_user') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', 'ai_user', 'CHANGE_ME_AI');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_user') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', 'admin_user', 'CHANGE_ME_ADMIN');
    END IF;
END $$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO ai_user, admin_user', current_database());
END $$;

-- AI-бот: читает конфигурацию, заводит пользователей и чаты, пишет в ai.
GRANT USAGE ON SCHEMA shared, config, ai TO ai_user;
GRANT SELECT ON ALL TABLES IN SCHEMA shared, config TO ai_user;
GRANT INSERT, UPDATE ON shared.tg_users, shared.tg_chats TO ai_user;
-- Бот отмечает время последнего срыва персоны, остальную карточку правит админка.
GRANT UPDATE ON config.personas TO ai_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ai TO ai_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ai TO ai_user;

-- Админка: читает общие и ai, правит config и configurator, пишет память, базовые
-- значения эмоций и историю чата при смене персоны.
GRANT USAGE ON SCHEMA shared, config, ai, configurator TO admin_user;
GRANT SELECT ON ALL TABLES IN SCHEMA shared, ai TO admin_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA config, configurator TO admin_user;
GRANT INSERT, UPDATE, DELETE ON ai.persona_memories, ai.memory_people,
    ai.persona_emotions, ai.ai_messages TO admin_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA config, configurator, ai TO admin_user;

-- Таблицы и последовательности, которые появятся в следующих миграциях.
-- В ai админка по умолчанию только читает: запись в память выдаётся точечно.
ALTER DEFAULT PRIVILEGES IN SCHEMA ai
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ai_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA ai
    GRANT USAGE, SELECT ON SEQUENCES TO ai_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA ai
    GRANT SELECT ON TABLES TO admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA config, configurator
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA config, configurator, ai
    GRANT USAGE, SELECT ON SEQUENCES TO admin_user;
