# Proposal

## Why

Приложение собрано вручную: `Main` читает конфигурацию статическими методами, `TelegramBot` создаёт девять сервисов и репозиториев через `new` прямо в полях, `DataSourceConfig.initialize(...)` поднимает HikariCP в статическом поле, а валидация параметров сводится к проверке на пустоту и `System.exit(1)`. Из-за этого связность фиксирована, подменить компонент в тесте нельзя, а опечатка в конфигурации обнаруживается уже после подключения к БД. Задача #39 — перевести бота на Spring Boot 3.x без изменения поведения (issue #39, GitHub).

## What Changes

- Spring Boot 3.x: `spring-boot-starter-parent`, `spring-boot-starter` (без web), `spring-boot-starter-jdbc`, `spring-boot-starter-validation`; версии Jackson/slf4j/logback/HikariCP перестают быть явными. `org.telegram:telegrambots:6.5.0` остаётся закреплённой явно.
- **BREAKING**: конфигурация читается только из переменных окружения `BOT_*` через `@ConfigurationProperties` + `@Validated`. Позиционные аргументы (`token dbUrl dbUser dbPassword aiKey`) больше не поддерживаются — Spring принимает только `--bot.db.url=...`.
- **BREAKING**: невалидный `BOT_PROXY_PORT` при заданном `BOT_PROXY_HOST` останавливает старт с ошибкой вместо тихого отключения прокси.
- Сервисы, репозитории, `SettingsService`, `AiService`, игры, `TelegramBotsApi` и `TelegramLongPollingBot` становятся бинами контекста; ручные `new` в полях удаляются.
- `DataSource` строится из `BotProperties` (имена `BOT_DB_*` не совпадают с `spring.datasource.*`), `DataSourceConfig` удаляется.
- Цикл из трёх попыток `registerBot` сохраняется, но переезжает в `SmartLifecycle.start()`; `stop()` вызывает `BotSession.stop()`, поэтому `systemctl restart` (SIGTERM) больше не оставляет висящий long polling.
- Сборка: `spring-boot-maven-plugin` (`repackage`) вместо `maven-shade-plugin`; имя артефакта `target/telegram-bot-1.0.jar` не меняется, `deploy.yml` и `deploy/telegram-bot.service` правок не требуют.
- Бизнес-логика игр, AI и обработчиков команд не переписывается; миграции остаются ручными SQL-скриптами в `migrations/`.

## Capabilities

### New Capabilities

- `application-bootstrap`: как приложение собирается в исполняемый артефакт, стартует (регистрация бота с повторами) и корректно останавливается по SIGTERM.

### Modified Capabilities

- `runtime-configuration`: источник конфигурации запуска меняется с аргументов командной строки на переменные окружения `BOT_*`, добавляется обязательная валидация параметров при старте.

## Impact

- Код: `Main.java`, `TelegramBot.java`, `DataSourceConfig.java`, `CommandDispatcher.java`, `CallbackDispatcher.java`, `UserChatManager.java`, `MessageSender.java`, `KeyboardBuilder.java`, сервисы и репозитории в `Users/`, `Chats/`, `PidorGame/`, `CockSize/`, `Horoscope/`, `AiChat/`, `Settings/`.
- Конфигурация и сборка: `pom.xml`; `dependency-reduced-pom.xml` (артефакт shade) удаляется; `src/main/resources/logback.xml` сохраняется.
- Инфраструктура: без изменений — `/etc/telegram-bot/bot.env`, `deploy/telegram-bot.service`, `.github/workflows/deploy.yml` остаются как есть.
- Локальный запуск: `.vscode/launch.json` (не отслеживается git) переводится с блока `args` на `env` с теми же `${env:...}`.
- Риск: `Restart=always` + `StartLimitIntervalSec=0` в юните превращают неверный `bot.env` в бесконечный цикл рестартов; поведение осознанно оставляется прежним и фиксируется в `design.md` и `tasks.md`.
