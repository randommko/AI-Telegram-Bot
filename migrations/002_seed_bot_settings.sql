-- Seed минимальных настроек ИИ, необходимых для запуска бота без NPE.
-- Идемпотентная миграция: ON CONFLICT DO NOTHING не перезаписывает существующие значения.
--
-- ВНИМАНИЕ: промпты aiPromtContext/aiPromtMyChatContext заданы нейтральными заглушками.
-- Реальные значения в продакшн-БД содержат персональные данные участников чата
-- и в репозиторий не попадают. Для полного поведения ИИ их нужно выставить вручную.
SET client_encoding TO 'UTF8';

INSERT INTO public.bot_settings (setting_key, value)
VALUES
    ('aiAnswerTemperature', '1.8F'),
    ('maxTokensQuestion', '1000'),
    ('aiPromtContext', 'Ты — участник дружеского чата.'),
    ('aiPromtMyChatContext', 'Ты — постоянный участник дружеского группового чата.')
ON CONFLICT (setting_key) DO NOTHING;
