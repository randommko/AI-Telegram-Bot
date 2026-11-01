# Tasks

## 1. Снятие схемы и seed-данных с удалённой БД

- [x] 1.1 Подключиться к PostgreSQL `45.141.103.51` (`telegram_bot_db`, `app_user`) и снять DDL всех таблиц (`pg_dump --schema-only` или интроспекция). Verify: получен точный список таблиц и колонок (`tg_users`, `tg_chats`, `pidor_players`, `pidor_stats`, `pidor_messages`, `cocksize_stats`, `cocksize_imgs`, `bot_settings`).
- [x] 1.2 Снять значения ключей `bot_settings`. Verify: есть значения `aiAnswerTemperature`, `maxTokensQuestion`, `aiPromtContext`, `aiPromtMyChatContext` (или зафиксированы разумные дефолты).

## 2. Рефакторинг запуска: удаление прокси и переход на аргументы CLI

- [x] 2.1 Удалить весь прокси-код из `Main.java` (`proxy`-парсинг, `System.setProperty`, `Authenticator`, `DefaultBotOptions`) и переписать чтение конфигурации на `args[0..4]`. Verify: `mvn -q compile` завершается без ошибок, в `Main.java` нет упоминаний `config.json` и прокси.
- [x] 2.2 Изменить конструктор `TelegramBot` на `(String botToken, String aiToken)` с вызовом `super()`; обновить вызов в `Main`. Verify: компиляция проходит, `DefaultBotOptions` больше не используется.
- [x] 2.3 Удалить `ListJar.java` и `ListJar.class` из корня. Verify: файлы отсутствуют, сборка не ссылается на них.

## 3. Очистка мёртвого кода

- [x] 3.1 Удалить константы `QUIZ_QUESTION_TABLE`, `QUIZ_ANSWERS_TABLE`, `QUIZ_STATS_TABLE` из `Settings/TablesDB.java`. Verify: `grep -ri quiz src` не находит совпадений.
- [x] 3.2 Удалить неиспользуемые константы `QUOTE_TABLE` и `MESSAGE_HISTORY_TABLE`. Verify: `grep` по этим именам не находит использования вне определения.

## 4. SQL-миграции

- [x] 4.1 Создать `migrations/001_init_schema.sql` с `CREATE TABLE IF NOT EXISTS` для всех таблиц из п.1.1. Verify: накат на пустую БД через `psql` создаёт все таблицы без ошибок.
- [x] 4.2 Создать `migrations/002_seed_bot_settings.sql` с `INSERT ... ON CONFLICT DO NOTHING` для ключей из п.1.2. Verify: после наката старт бота не вызывает NPE в `AiService`/`DeepSeekClient`.
- [x] 4.3 Проверить идемпотентность миграций. Verify: повторный накат обоих скриптов завершается успешно и не дублирует строки/не ломает схему.

## 5. CI/CD

- [x] 5.1 Выровнять `.github/workflows/deploy.yml` под порядок аргументов `Main` (`token dbUrl dbUser dbPassword aiKey`) и подтвердить inject из GitHub Secrets. Verify: чтение `deploy.yml` показывает те же пять секретов в том же порядке.

## 6. Интеграционная проверка (отложена)

Локальный запуск и проверка long polling отложены: на этой сети `api.telegram.org` недоступен напрямую, а возврат прокси вынесен в отдельное изменение (решение пользователя от 2026-11-01). Сборка выполнена (см. follow-up ниже).

## Workflow follow-up

- Сборка `mvn clean package` выполнена: `target/telegram-bot-1.0.jar` собран (с `JAVA_HOME=C:\Program Files\Java\jdk-17`).
- Вернуть конфигурируемый прокси (HTTP/SOCKS5 через env-переменные) отдельным изменением.
- После возврата прокси повторить: локальный запуск с проверкой «Бот успешно запущен» и `/bot_info`, а также проверка long polling.
- Архив изменения после ревью и успешного деплоя.
- Ротация пароля БД, попавшего в чат.
