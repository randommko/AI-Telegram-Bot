# Proposal

## Why

Стартовый путь бота переусложнён и частично сломан: приложение читает gitignored `config.json`, которого нет в репозитории, всё ещё содержит неиспользуемый SOCKS5-прокси-код, а CI/CD (`deploy.yml`) передаёт секреты аргументами командной строки, которые текущий `Main` игнорирует. В репозитории также нет миграций БД, поэтому схему нельзя воспроизвести. Это блокирует цель — запустить бота локально, а затем задеплоить на VPS.

## What Changes

- **BREAKING**: Удалена поддержка SOCKS5-прокси (парсинг конфига, `System.setProperty`, `Authenticator`, `DefaultBotOptions`) и вспомогательный файл `ListJar.java` / `ListJar.class`.
- **BREAKING**: Конфигурация запуска переведена с файла `config.json` на аргументы командной строки: `token dbUrl dbUser dbPassword aiKey`. Файл `config.json` больше не требуется.
- Добавлены версионированные SQL-миграции для схемы PostgreSQL, включая seed-данные таблицы `bot_settings`.
- Удалены «мёртвые» константы таблиц quiz (`QUIZ_*`) и другие неиспользуемые константы из `TablesDB`.
- Выровнен `deploy.yml`: секреты GitHub передаются как аргументы запуска (порядок уже совпадает).
- Подключение к удалённой БД `45.141.103.51` (`telegram_bot_db`, пользователь `app_user`) через аргументы/секреты.

## Capabilities

### New Capabilities

- `runtime-configuration`: как бот конфигурируется при запуске (аргументы CLI, без прокси) и как подключается к PostgreSQL с версионированной схемой.

### Modified Capabilities

<!-- Существующих спеков нет; изменяемых capability нет. -->

## Impact

- Код: `Main.java`, `TelegramBot.java`, `Settings/TablesDB.java`.
- Новое: директория `migrations/` с SQL-скриптами.
- CI/CD: `.github/workflows/deploy.yml`.
- Удаление: `ListJar.java`, `ListJar.class`.
- Зависимости: без новых runtime-зависимостей (миграции применяются вручную через `psql`).
- `config.json` больше не используется и не требуется.
