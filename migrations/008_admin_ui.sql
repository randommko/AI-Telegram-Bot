-- Админка: подсказки к полям, описания настроек и один общий промпт (issue #66).
-- Идемпотентная миграция: безопасно накатывать повторно.
SET client_encoding TO 'UTF8';

-- Подсказки к параметрам персоны. Описания эмоций и настроек живут в своих таблицах,
-- здесь только то, у чего описания не было: шесть числовых параметров карточки.
CREATE TABLE IF NOT EXISTS public.persona_field_notes (
    field_key character varying(64) PRIMARY KEY,
    note text NOT NULL
);

INSERT INTO public.persona_field_notes (field_key, note)
VALUES
    ('aggressionThreshold',
     'Уровень агрессии (0..10), при котором персона срывается, если её только что оскорбили. '
     'Агрессия считается из эмоций: гнев, брезгливость, сарказм и зависть тянут её вверх, радость гасит.'),
    ('hysteriaReplies',
     'Сколько сообщений подряд отправит персона во время срыва. 3-5: меньше — не похоже на срыв, '
     'больше — превращается в спам.'),
    ('hysteriaCooldownHours',
     'Пауза между срывами в часах. Пока она не прошла, новый срыв не начнётся, '
     'даже если агрессия выше порога.'),
    ('maxReplies',
     'Сколько реплик максимум может отправить один ответ. 1 — обычное сообщение, '
     'больше — серия реплик с паузами.'),
    ('replyDelayMs',
     'Пауза между репликами в миллисекундах: 1500 — полторы секунды, чтобы ответ выглядел живой перепиской.'),
    ('initiativeCooldownMessages',
     'Через сколько сообщений чата персона может снова влезть в беседу без обращения. '
     'Защита от спама: пока не наберётся столько чужих сообщений, инициативы не будет.')
ON CONFLICT (field_key) DO NOTHING;

-- Получатель уведомления о запуске живёт в базе, но отдельной миграцией не заводился:
-- на чистой установке строки нет, поэтому создаём пустую — уведомления просто отключатся.
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES ('ownerChatId', '',
        'Telegram ID получателя сообщения «бот запустился»; пусто — уведомление не отправляется. '
        'В личном чате ID совпадает с user_id.')
ON CONFLICT (setting_key) DO NOTHING;

-- Описания, которых не было: они показываются подсказкой у поля в настройках.
UPDATE public.bot_settings
SET description = 'Насколько свободно модель формулирует ответ. 0 — строго и повторяемо, '
                  '2 — максимально непредсказуемо; выше 1.0 персона начинает фантазировать.'
WHERE setting_key = 'aiAnswerTemperature'
  AND (description IS NULL OR btrim(description) = '');

UPDATE public.bot_settings
SET description = 'Сколько токенов максимум модель может потратить на ответ. У reasoning-модели '
                  'часть бюджета уходит на размышление, поэтому слишком маленькое значение даёт пустой ответ.'
WHERE setting_key = 'maxTokensQuestion'
  AND (description IS NULL OR btrim(description) = '');

UPDATE public.bot_settings
SET description = 'Промпт любого чата без персоны, включая команду /ии. '
                  'Если у чата есть персона, используется её промпт вместо этого.'
WHERE setting_key = 'aiPromtContext'
  AND (description IS NULL OR btrim(description) = '');

UPDATE public.bot_settings
SET description = 'Telegram ID получателя сообщения «бот запустился»; пусто — уведомление не отправляется. '
                  'В личном чате ID совпадает с user_id.'
WHERE setting_key = 'ownerChatId'
  AND (description IS NULL OR btrim(description) = '');

-- Отдельный промпт «синего» чата больше не нужен: у чата без персоны один общий промпт.
-- Значение этой строки не восстановить, поэтому перед накатом его копируют вручную.
DELETE FROM public.bot_settings WHERE setting_key = 'aiPromtMyChatContext';

-- Права роли бота на новую таблицу.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user';
    END IF;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Права для app_user не выданы: нужен запуск от владельца схемы (admin)';
END $$;
