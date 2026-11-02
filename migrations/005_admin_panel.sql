-- Админка: модель в настройках и уборка мёртвых ключей (issue #61).
-- Идемпотентная миграция: безопасно накатывать повторно.
SET client_encoding TO 'UTF8';

-- Модель теперь читается из настроек на каждый запрос; стартовое значение — прежняя константа.
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES ('aiModel', 'deepseek-flash', 'Модель для ответов и анализа памяти, применяется без перезапуска')
ON CONFLICT (setting_key) DO NOTHING;

-- Мёртвые настройки: команда /summary объявлена, но обработчика у неё нет,
-- поэтому ни промпт, ни температура суммаризации никем не читаются.
DELETE FROM public.bot_settings
WHERE setting_key IN ('aiSummaryTemperature', 'aiPromtSummaryContext');

-- Права роли бота: на случай, если 004 накатывали без блока GRANT.
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
