-- Каталог эмодзи для реакций и настройки прохода чтения.
-- Набор разрешённых эмодзи задан кодом; здесь лежат только текст «когда ставим»
-- и два признака. Повторный накат не перезаписывает правки администратора.
SET client_encoding TO 'UTF8';

CREATE TABLE IF NOT EXISTS config.reaction_emojis (
    emoji text PRIMARY KEY,
    meaning text,
    is_memory boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT reaction_emojis_active_meaning
        CHECK (NOT active OR (meaning IS NOT NULL AND btrim(meaning) <> ''))
);

-- Стартовый набор: включённые реакции отношения и метка памяти.
-- Если строки уже есть, повторный накат их не трогает.
INSERT INTO config.reaction_emojis (emoji, meaning, is_memory, active, sort_order)
VALUES
    ('😁', 'смешно, весело', false, true, 70),
    ('🤣', 'очень смешно', false, true, 250),
    ('🥰', 'тепло, симпатия', false, true, 40),
    ('🔥', 'сильно сказано, восхищение', false, true, 50),
    ('🤔', 'сомнение, не верю', false, true, 80),
    ('🤬', 'злость, оскорбили', false, true, 110),
    ('😡', 'раздражение, злость', false, true, 160),
    ('👎', 'не согласен, презрение', false, true, 20),
    ('🤡', 'клоунада, самоуверенная глупость', false, true, 200),
    ('💩', 'глупость, мерзость', false, true, 170),
    ('😢', 'грусть, жаль', false, true, 120),
    ('🎉', 'радость за событие', false, true, 420),
    ('🕊', 'перемирие после извинения', false, true, 190),
    ('✍', 'сообщение попало в память', true, true, 390)
ON CONFLICT (emoji) DO NOTHING;

-- Остальные разрешённые Telegram эмодзи: выключены, пока администратор не включит.
INSERT INTO config.reaction_emojis (emoji, meaning, is_memory, active, sort_order)
VALUES
    ('👍', NULL, false, false, 10),
    ('👎', NULL, false, false, 20),
    ('❤', NULL, false, false, 30),
    ('🥰', NULL, false, false, 40),
    ('🔥', NULL, false, false, 50),
    ('👏', NULL, false, false, 60),
    ('😁', NULL, false, false, 70),
    ('🤔', NULL, false, false, 80),
    ('🤯', NULL, false, false, 90),
    ('😱', NULL, false, false, 100),
    ('🤬', NULL, false, false, 110),
    ('😢', NULL, false, false, 120),
    ('😍', NULL, false, false, 130),
    ('🤩', NULL, false, false, 140),
    ('😮', NULL, false, false, 150),
    ('😡', NULL, false, false, 160),
    ('💩', NULL, false, false, 170),
    ('👌', NULL, false, false, 180),
    ('🕊', NULL, false, false, 190),
    ('🤡', NULL, false, false, 200),
    ('🥱', NULL, false, false, 210),
    ('🥴', NULL, false, false, 220),
    ('😈', NULL, false, false, 230),
    ('💯', NULL, false, false, 240),
    ('🤣', NULL, false, false, 250),
    ('😘', NULL, false, false, 260),
    ('💔', NULL, false, false, 270),
    ('🤨', NULL, false, false, 280),
    ('😎', NULL, false, false, 290),
    ('🍾', NULL, false, false, 300),
    ('😭', NULL, false, false, 310),
    ('😴', NULL, false, false, 320),
    ('😔', NULL, false, false, 330),
    ('🤓', NULL, false, false, 340),
    ('😇', NULL, false, false, 350),
    ('😏', NULL, false, false, 360),
    ('😌', NULL, false, false, 370),
    ('🤝', NULL, false, false, 380),
    ('✍', NULL, false, false, 390),
    ('🤗', NULL, false, false, 400),
    ('🫡', NULL, false, false, 410),
    ('🎉', NULL, false, false, 420),
    ('⚡', NULL, false, false, 430),
    ('🏆', NULL, false, false, 440),
    ('💐', NULL, false, false, 450)
ON CONFLICT (emoji) DO NOTHING;

-- Настройки прохода чтения. Функция выключена, пока владелец не включит её в админке.
INSERT INTO config.bot_settings (setting_key, value, description)
VALUES
    ('aiReactionsEnabled', 'false',
     'Ставить реакции на прочитанные сообщения чата. Выключено — проход не запускается и модель не вызывается.'),
    ('aiReactionPrompt',
     'Ты читаешь переписку чата и решаешь, на что у тебя есть заметное отношение.
Поставь реакцию не больше чем двум сообщениям — самым ярким. Обычная болтовня, согласия, приветствия и «ок» остаются без реакции: реакция — это мнение, а не подтверждение, что ты прочитал.
Реагируй только эмодзи из списка ниже, по одному на сообщение.
Ответ — строго JSON без пояснений:
{"reactions":[{"message_id":123,"emoji":"🤡","reason":"почему именно так"}]}
Если реагировать не на что — {"reactions":[]}.',
     'Инструкция модели для прохода чтения. Список включённых эмодзи дописывается кодом.'),
    ('aiReactionPoolSize', '20',
     'Сколько сообщений накапливать в пачке и сколько оценивать за один проход.'),
    ('aiReactionPauseSeconds', '30',
     'Сколько секунд тишины после последнего сообщения ждать перед проходом.'),
    ('aiReactionMaxTokens', '2000',
     'Сколько токенов модель может потратить на один проход реакций. Рассуждения модели списываются из того же лимита: при малом значении рассуждающая модель вернёт пустой ответ и пачка реакций пропустится.'),
    ('aiReactionModelUseOwn', 'false',
     'Считать реакции отдельной моделью, а не основной моделью ответов.'),
    ('aiReactionModel', '',
     'Модель для реакций. Пусто или выключенный флажок — используется основная модель.'),
    ('aiReactionRoute', '',
     'Провайдер модели реакций; пусто — провайдер выбранной модели.')
ON CONFLICT (setting_key) DO NOTHING;
