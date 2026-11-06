-- Затухание важности событий: сколько дней нужно, чтобы важность упала на балл.
-- 0 — затухание выключено. Значение по умолчанию совпадает с MemoryDecay.DEFAULT_DAYS_PER_POINT.
SET client_encoding TO 'UTF8';

INSERT INTO config.bot_settings (setting_key, value, description)
VALUES ('aiMemoryEventDecayDays', '3',
        'Через сколько дней важность события падает на 1 балл. 0 — важность не снижается. Друзья, семья, хобби и работа не стареют.')
ON CONFLICT (setting_key) DO NOTHING;
