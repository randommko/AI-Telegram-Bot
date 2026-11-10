-- Подъём дефолтных бюджетов ответа и плана: у моделей polza рассуждения списываются
-- из того же лимита вывода, и старых 1000/1500 флагманам с обязательными рассуждениями
-- не хватает — ответ или план приходит пустым. Меняются только строки со старым дефолтом,
-- осознанно изменённые администратором значения не трогаются.
SET client_encoding TO 'UTF8';

UPDATE config.bot_settings SET value = '4096'
WHERE setting_key = 'maxTokensQuestion' AND value = '1000';

UPDATE config.bot_settings SET value = '4096'
WHERE setting_key = 'aiPlanMaxTokens' AND value = '1500';
