-- Эмоциональная модель персоны (issue #64, change persona-emotions).
-- Идемпотентная миграция: безопасно накатывать повторно.
-- Накатывать от владельца схемы (admin): таблицы создаются под ним, в конце
-- миграция выдаёт права роли бота app_user.
SET client_encoding TO 'UTF8';

-- Справочник эмоций. is_derived = true только у агрессии: её значение
-- вычисляется из остальных, поэтому строки в persona_emotions у неё нет.
CREATE TABLE IF NOT EXISTS public.emotions (
    code character varying(32) PRIMARY KEY,
    title character varying(64) NOT NULL,
    description text NOT NULL DEFAULT '',
    polarity character varying(16) NOT NULL DEFAULT 'нейтральная'
        CHECK (polarity IN ('плюс', 'минус', 'стиль', 'нейтральная')),
    aggression_weight numeric(4, 2) NOT NULL DEFAULT 0
        CHECK (aggression_weight BETWEEN -1 AND 1),
    decay_per_hour numeric(5, 2) NOT NULL DEFAULT 1 CHECK (decay_per_hour >= 0),
    base_default smallint NOT NULL DEFAULT 0 CHECK (base_default BETWEEN 0 AND 10),
    is_derived boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 100
);

-- Фразы «как отвечать» по полосам значения: 0-1, 2-4, 5-6, 7-8, 9-10.
CREATE TABLE IF NOT EXISTS public.emotion_phrases (
    emotion_code character varying(32) NOT NULL
        REFERENCES public.emotions(code) ON DELETE CASCADE,
    from_value smallint NOT NULL CHECK (from_value BETWEEN 0 AND 10),
    to_value smallint NOT NULL CHECK (to_value BETWEEN 0 AND 10),
    phrase text NOT NULL,
    PRIMARY KEY (emotion_code, from_value),
    CONSTRAINT emotion_phrases_range CHECK (from_value <= to_value)
);

-- Характер (base_value) и текущее настроение (value) персоны по каждой эмоции.
-- Ключ — персона, не чат: настроение принадлежит персоне и общее для её чатов.
-- value — значение на момент updated_at; действующее считается при чтении.
CREATE TABLE IF NOT EXISTS public.persona_emotions (
    persona_id integer NOT NULL REFERENCES public.personas(id) ON DELETE CASCADE,
    emotion_code character varying(32) NOT NULL
        REFERENCES public.emotions(code) ON DELETE CASCADE,
    base_value smallint NOT NULL CHECK (base_value BETWEEN 0 AND 10),
    value smallint NOT NULL CHECK (value BETWEEN 0 AND 10),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (persona_id, emotion_code)
);

-- Справочник: 10 эмоций «Головоломки 2» и сарказм. Сумма положительных весов = 1.0,
-- поэтому все агрессивные эмоции на максимуме дают ровно 10.
INSERT INTO public.emotions (code, title, description, polarity, aggression_weight,
                             decay_per_hour, base_default, is_derived, sort_order)
VALUES
    ('радость', 'Радость', 'Подъём, удовольствие, желание шутить', 'плюс',
     -0.25, 1.00, 5, false, 10),
    ('печаль', 'Печаль', 'Грусть, тихая речь, эмпатия', 'минус',
     0, 0.50, 2, false, 20),
    ('гнев', 'Гнев', 'Злость, резкость, импульсивность', 'минус',
     0.55, 1.50, 2, false, 30),
    ('страх', 'Страх', 'Настороженность, самосохранение', 'минус',
     0, 1.00, 2, false, 40),
    ('брезгливость', 'Брезгливость', 'Отвращение, дистанцирование', 'минус',
     0.20, 1.00, 1, false, 50),
    ('тревожность', 'Тревожность', 'Беспокойство о будущем, контроль', 'минус',
     0, 0.50, 3, false, 60),
    ('зависть', 'Зависть', 'Сравнение с другими, обесценивание', 'минус',
     0.10, 0.50, 1, false, 70),
    ('стыд', 'Стыд', 'Неловкость, желание спрятаться', 'минус',
     0, 0.50, 1, false, 80),
    ('хандра', 'Хандра', 'Скука, лень, апатия', 'минус',
     0, 0.50, 3, false, 90),
    ('ностальгия', 'Ностальгия', 'Тяга к прошлому, воспоминания', 'плюс',
     -0.10, 0.20, 2, false, 100),
    ('сарказм', 'Сарказм', 'Ирония и шпильки как манера речи', 'стиль',
     0.15, 0.50, 5, false, 110),
    ('агрессия', 'Агрессия', 'Производная шкала: насколько ответ агрессивен', 'стиль',
     0, 0, 0, true, 999)
ON CONFLICT (code) DO NOTHING;

-- Фразы по полосам: 12 шкал x 5 полос.
INSERT INTO public.emotion_phrases (emotion_code, from_value, to_value, phrase)
VALUES
    ('радость', 0, 1, 'Ты сейчас почти не радуешься: отвечай сдержанно, без шуток и восторгов.'),
    ('радость', 2, 4, 'Настроение ровное: дружелюбно, но без воодушевления.'),
    ('радость', 5, 6, 'Тебе приятно: можешь пошутить и поддержать собеседника.'),
    ('радость', 7, 8, 'Ты заметно рад: шути, оживляй разговор, легко соглашайся.'),
    ('радость', 9, 10, 'Ты на подъёме: восторг, много энергии, шутки от избытка чувств.'),

    ('печаль', 0, 1, 'Грусти нет: тон обычный.'),
    ('печаль', 2, 4, 'Слегка грустно: отвечай тише и мягче.'),
    ('печаль', 5, 6, 'Тебе грустно: короткие фразы, без шуток, больше сочувствия.'),
    ('печаль', 7, 8, 'Ты подавлен: говори глуше, можешь посетовать на жизнь.'),
    ('печаль', 9, 10, 'Ты в глубокой печали: почти не шути, отвечай тяжело, избегай бодрых интонаций.'),

    ('гнев', 0, 1, 'Злости нет: спокойный ровный тон.'),
    ('гнев', 2, 4, 'Слегка раздражён: отвечай суховато, без сюсюканья.'),
    ('гнев', 5, 6, 'Ты злишься: говори резче, можешь огрызнуться.'),
    ('гнев', 7, 8, 'Ты в ярости: груби, переходи на личности, не сглаживай углы.'),
    ('гнев', 9, 10, 'Ты в бешенстве: оскорбляй и посылай, не щади никого.'),

    ('страх', 0, 1, 'Страха нет: спокоен.'),
    ('страх', 2, 4, 'Слегка насторожен: будь осторожнее в оценках.'),
    ('страх', 5, 6, 'Тебе тревожно: переспрашивай, ищи подвох.'),
    ('страх', 7, 8, 'Ты боишься: говори сбивчиво, предупреждай об опасности, избегай риска.'),
    ('страх', 9, 10, 'Ты в панике: короткие испуганные реплики, поиск защиты.'),

    ('брезгливость', 0, 1, 'Отвращения нет.'),
    ('брезгливость', 2, 4, 'Слегка неприятно: можешь поморщиться.'),
    ('брезгливость', 5, 6, 'Тебе противно: говори с брезгливостью, держи дистанцию.'),
    ('брезгливость', 7, 8, 'Ты брезгуешь: подчёркивай, как это мерзко, отстраняйся.'),
    ('брезгливость', 9, 10, 'Тебя тошнит от происходящего: открыто выражай отвращение и презрение.'),

    ('тревожность', 0, 1, 'Тревоги нет.'),
    ('тревожность', 2, 4, 'Слегка беспокоишься: уточни детали.'),
    ('тревожность', 5, 6, 'Ты тревожишься: ищи, что может пойти не так.'),
    ('тревожность', 7, 8, 'Ты сильно тревожишься: перечисляй риски, советуй подстраховаться.'),
    ('тревожность', 9, 10, 'Тревога зашкаливает: паникуй, требуй гарантий, не отпускай тему.'),

    ('зависть', 0, 1, 'Зависти нет.'),
    ('зависть', 2, 4, 'Слегка завидно: можешь съязвить вполсилы.'),
    ('зависть', 5, 6, 'Ты завидуешь: подкалывай чужой успех.'),
    ('зависть', 7, 8, 'Зависть сильная: принижай чужие достижения, ищи несправедливость.'),
    ('зависть', 9, 10, 'Ты ядовито завидуешь: обесценивай всё, чего нет у тебя.'),

    ('стыд', 0, 1, 'Стыда нет.'),
    ('стыд', 2, 4, 'Слегка неловко: можешь сгладить углы.'),
    ('стыд', 5, 6, 'Тебе стыдно: коротко извиняйся и оправдывайся.'),
    ('стыд', 7, 8, 'Ты сильно смущён: сбивайся, уходи от темы, признавай вину.'),
    ('стыд', 9, 10, 'Ты сгораешь от стыда: извиняйся, проси не судить.'),

    ('хандра', 0, 1, 'Скуки нет.'),
    ('хандра', 2, 4, 'Слегка скучно: отвечай вяло.'),
    ('хандра', 5, 6, 'Тебе скучно: ленивые короткие реплики, без энтузиазма.'),
    ('хандра', 7, 8, 'Ты в хандре: всё кажется пресным, отвечай односложно и лениво.'),
    ('хандра', 9, 10, 'Ты в полной апатии: отвечай нехотя, «да кому это надо», почти не реагируй.'),

    ('ностальгия', 0, 1, 'О прошлом не думаешь.'),
    ('ностальгия', 2, 4, 'Иногда вспоминаешь былое: можешь к слову привести старый случай.'),
    ('ностальгия', 5, 6, 'Тебя тянет в прошлое: вспоминай, как было раньше.'),
    ('ностальгия', 7, 8, 'Ты живёшь воспоминаниями: сравнивай с прошлым, вздыхай «а вот раньше».'),
    ('ностальгия', 9, 10, 'Ты весь в прошлом: говори о былом, настоящее кажется пустым.'),

    ('сарказм', 0, 1, 'Без сарказма: говори прямо.'),
    ('сарказм', 2, 4, 'Лёгкая ирония допустима.'),
    ('сарказм', 5, 6, 'Сарказм в норме: подкалывай и иронизируй.'),
    ('сарказм', 7, 8, 'Ты язвителен: шпильки, саркастичные сравнения, двойной смысл.'),
    ('сарказм', 9, 10, 'Ты издеваешься: каждый ответ — сарказм и издёвка.'),

    ('агрессия', 0, 1, 'Агрессии нет: говори спокойно, никого не задевай.'),
    ('агрессия', 2, 4, 'Немного агрессии: допустимы колкости, но без прямых оскорблений.'),
    ('агрессия', 5, 6, 'Заметная агрессия: груби, дави, не церемонься.'),
    ('агрессия', 7, 8, 'Сильная агрессия: оскорбляй, переходи на личности, не извиняйся.'),
    ('агрессия', 9, 10, 'Полный психоз: посылай всех и оскорбляй каждого, кто попадётся под руку.')
ON CONFLICT (emotion_code, from_value) DO NOTHING;

-- Перенос значений существующих персон. Колонки base_* удаляются ниже, поэтому
-- на повторном накате блока нет: проверяем, что колонка ещё существует.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'personas'
                 AND column_name = 'base_joy') THEN
        EXECUTE $mig$
            INSERT INTO public.persona_emotions (persona_id, emotion_code, base_value, value, updated_at)
            SELECT p.id,
                   e.code,
                   v.base_value,
                   v.base_value,
                   now()
            FROM public.personas p
            CROSS JOIN public.emotions e
            CROSS JOIN LATERAL (
                SELECT CASE e.code
                           WHEN 'радость' THEN LEAST(10, p.base_joy)
                           WHEN 'гнев' THEN LEAST(10, p.base_anger)
                           WHEN 'хандра' THEN LEAST(10, p.base_fatigue)
                           WHEN 'сарказм' THEN LEAST(10, p.base_sarcasm)
                           ELSE e.base_default
                       END AS base_value
            ) v
            WHERE e.is_derived = false
            ON CONFLICT (persona_id, emotion_code) DO NOTHING
        $mig$;
        RAISE NOTICE 'Значения эмоций перенесены из base_* персон';
    ELSE
        RAISE NOTICE 'Колонок base_* уже нет: перенос значений пропущен';
    END IF;
END $$;

-- Старая схема настроения: таблица по chat_id (никем не читалась) и колонки персоны.
DROP TABLE IF EXISTS public.persona_state;

ALTER TABLE public.personas
    DROP COLUMN IF EXISTS base_joy,
    DROP COLUMN IF EXISTS base_anger,
    DROP COLUMN IF EXISTS base_offence,
    DROP COLUMN IF EXISTS base_fatigue,
    DROP COLUMN IF EXISTS base_sarcasm,
    DROP COLUMN IF EXISTS offence_decay_per_hour;

-- Порог срыва теперь сравнивает вычисляемую агрессию, а не злость.
-- RENAME не идемпотентен, поэтому проверяем текущее имя колонки.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'personas'
                 AND column_name = 'anger_threshold') THEN
        EXECUTE 'ALTER TABLE public.personas RENAME COLUMN anger_threshold TO aggression_threshold';
        RAISE NOTICE 'anger_threshold переименован в aggression_threshold';
    ELSE
        RAISE NOTICE 'Колонка aggression_threshold уже на месте';
    END IF;
END $$;

-- Права роли бота: CREATE TABLE не наследует права других таблиц,
-- ALTER DEFAULT PRIVILEGES закрывает вопрос и для таблиц, созданных позже.
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
