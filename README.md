Бот для Telegram.

Команды:

- `/cocksize` — Измерить достоинство
- `/pidor_start` — Выбрать пидора дня
- `/horoscope_today` — Гороскоп
- `/pidor_reg` — Вступить в ряды пидоров
- `/pidor_stats` — Статистика пидоров
- `/ai` — Спроси ИИ
- `/clear` — Обнулить память AI (с подтверждением)
- `/summary` — Пересказ последних 100 сообщений

## Запуск

Требуется JDK 17. Сборка:

```
mvn clean package
```

Конфигурация передаётся только переменными окружения — позиционные аргументы командной строки не поддерживаются (они видны в `ps` любому локальному пользователю):

| Переменная | Значение |
|---|---|
| `BOT_TOKEN` | токен бота |
| `BOT_DB_URL` | JDBC-URL базы |
| `BOT_DB_USER` | пользователь базы |
| `BOT_DB_PASSWORD` | пароль базы |
| `BOT_AI_KEY` | ключ ИИ |

Windows (cmd):

```
set BOT_TOKEN=<token>
set BOT_DB_URL=jdbc:postgresql://host:5432/db
set BOT_DB_USER=<dbUser>
set BOT_DB_PASSWORD=<dbPassword>
set BOT_AI_KEY=<aiKey>
java -jar target/telegram-bot-1.0.jar
```

Linux:

```
BOT_TOKEN=<token> BOT_DB_URL=jdbc:postgresql://host:5432/db \
BOT_DB_USER=<dbUser> BOT_DB_PASSWORD=<dbPassword> BOT_AI_KEY=<aiKey> \
java -jar target/telegram-bot-1.0.jar
```

Обязательные переменные проверяются при старте: если значение пустое или не задано, приложение завершается с ненулевым кодом и сообщением, в котором указано имя переменной, не подключаясь к базе и Telegram.

## Уведомление о запуске

После старта бот присылает владельцу сообщение в личку: время запуска, часовой пояс, имя хоста, модель, температуру и лимит токенов для ответов. Получатель задаётся настройкой `ownerChatId` в таблице `bot_settings` — это Telegram ID пользователя (в личном чате `chat_id` совпадает с `user_id`). Если настройка отсутствует, пуста или содержит не число, уведомление не отправляется, а в лог пишется `WARN`.

```sql
INSERT INTO public.bot_settings (setting_key, value, description)
VALUES ('ownerChatId', '<telegram user id>', 'Кому присылать уведомления о запуске')
ON CONFLICT (setting_key) DO UPDATE SET value = EXCLUDED.value;
```

## Персона в «синем» чате

В чате `-1001347887839` отвечает персона «Юра». Её характер хранится в таблице `personas` (поле `prompt`), имена для обращения — в `persona_aliases`, привязка чата к персоне — в `chat_personas`. Промпт читается из базы на каждый запрос, поэтому правка применяется без перезапуска бота.

Сообщение без слэша вызывает ответ персоны, если:

- в нём есть алиас персоны как отдельное слово в любом месте сообщения: «юра, привет», «как дела, юра?». Слова вроде «юрист» и «юрта» обращением не считаются;
- сообщение является ответом (реплаем) на сообщение самого бота — имя в тексте не нужно.

Модели уходит исходный текст сообщения целиком. Если в сообщении только обращение, бот просит задать вопрос. Слэш-команды `/ai`, `/ии`, `/аи`, `/юра`, `/юрий`, `/юрец` работают в любом чате как раньше.

Промпт персоны приоритетнее настроек `aiPromtMyChatContext`/`aiPromtContext`; в чате без персоны бот берёт промпт из настроек, а при отсутствии настройки — дефолтный.

## Подключение к Telegram через прокси или VPN

Прокси опционален и задаётся переменными окружения. Если `BOT_PROXY_HOST` не задан — бот подключается к Telegram напрямую (это работает, если включён системный VPN в режиме TUN).

Переменные:

| Переменная | Описание |
|---|---|
| `BOT_PROXY_TYPE` | `HTTP` (по умолчанию) или `SOCKS5` |
| `BOT_PROXY_HOST` | Хост прокси; пусто/не задано — прямое подключение |
| `BOT_PROXY_PORT` | Порт прокси |
| `BOT_PROXY_USER` | Логин прокси (опционально) |
| `BOT_PROXY_PASS` | Пароль прокси (опционально) |

### Без прокси (системный VPN в режиме TUN)

```
java -jar target/telegram-bot-1.0.jar
```

### HTTP-прокси (например, локальный прокси VPN-клиента)

Windows (cmd):

```
set BOT_PROXY_TYPE=HTTP
set BOT_PROXY_HOST=127.0.0.1
set BOT_PROXY_PORT=12334
java -jar target/telegram-bot-1.0.jar
```

Linux:

```
BOT_PROXY_TYPE=HTTP BOT_PROXY_HOST=127.0.0.1 BOT_PROXY_PORT=12334 \
java -jar target/telegram-bot-1.0.jar
```

### SOCKS5-прокси (например, на VPS)

```
BOT_PROXY_TYPE=SOCKS5 \
BOT_PROXY_HOST=<socks-host> \
BOT_PROXY_PORT=<socks-port> \
BOT_PROXY_USER=<login> \
BOT_PROXY_PASS=<password> \
java -jar target/telegram-bot-1.0.jar
```

В CI/CD эти же переменные передаются из GitHub Secrets.

## Деплой

Пуш в `main` собирает jar и перезапускает службу на сервере (`.github/workflows/deploy.yml`). Коммиты, которые меняют только документацию — `**/*.md`, `openspec/**`, `.agents/**` — деплой не запускают, потому что ни на jar, ни на systemd-юнит они не влияют.
