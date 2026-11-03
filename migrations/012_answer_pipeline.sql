-- Конвейер ответа (issue #51, change answer-pipeline).
-- Идемпотентная миграция: безопасно накатывать повторно.
-- Накатывать от владельца схемы (admin): в конце выдаются права роли бота app_user.
SET client_encoding TO 'UTF8';

-- Время последнего срыва: пауза принадлежит персоне, как и настроение.
ALTER TABLE public.personas
    ADD COLUMN IF NOT EXISTS last_hysteria_at timestamp with time zone;

-- Настройки двухшагового ответа, обид и срыва.
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES
    ('aiPlanMaxTokens', '1500',
     'Сколько токенов отведено шагу «план». reasoning-модель тратит часть бюджета на размышления, '
     'поэтому слишком маленькое значение даёт пустой план и фолбэк на один запрос.'),
    ('aiGrudgeInsultStrength', '2',
     'Насколько растёт обида на участника за одно оскорбление в адрес персоны. Сила обиды ограничена 0..10.'),
    ('aiGrudgeAngerDelta', '2',
     'Насколько растёт гнев персоны за оскорбление в её адрес. Сверх сдвигов, которые вернул план ответа.'),
    ('aiGrudgeDecayPerHour', '1',
     'Сколько единиц обиды уходит за час. Обида затухает сама и не зависит от затухания эмоций.'),
    ('aiGrudgeThreshold', '5',
     'Минимальная действующая сила обиды, при которой участник становится адресатом колкостей.')
ON CONFLICT (setting_key) DO NOTHING;

-- Права роли бота на новую колонку.
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
