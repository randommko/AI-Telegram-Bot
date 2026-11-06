-- Порог, ниже которого важность события не падает при затухании.
-- 1 — прежнее поведение (важность не уходит ниже единицы).
SET client_encoding TO 'UTF8';

INSERT INTO config.bot_settings (setting_key, value, description)
VALUES ('aiMemoryEventMinImportance', '1',
        'Ниже этой важности событие не стареет: 1 — важность может упасть до единицы, 10 — событие всегда максимально важно. Порог не поднимает важность выше базовой, а друзья, семья, хобби и работа не стареют вовсе.')
ON CONFLICT (setting_key) DO NOTHING;
