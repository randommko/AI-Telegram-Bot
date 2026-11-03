-- Служебная строка про сбросы памяти больше не идёт в промпт: раздел удалён из кода.
-- Идемпотентная миграция: безопасно накатывать повторно.
SET client_encoding TO 'UTF8';

DELETE FROM public.bot_settings WHERE setting_key = 'aiPromptServiceTemplate';
