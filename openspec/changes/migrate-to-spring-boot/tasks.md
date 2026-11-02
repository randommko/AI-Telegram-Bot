# Tasks

## 1. Сборка на Spring Boot

- [x] 1.1 Перевести `pom.xml` на `spring-boot-starter-parent` 3.x и добавить `spring-boot-starter`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`; удалить `maven-shade-plugin`/`maven-jar-plugin`, добавить `spring-boot-maven-plugin` с `repackage`; оставить `org.telegram:telegrambots:6.5.0` закреплённой явно. Verify: `mvn -q clean package` собирает `target/telegram-bot-1.0.jar`, внутри архива есть `BOOT-INF/`, сам jar запускается через `java -jar`.
- [x] 1.2 Убрать явные версии `jackson-databind` (включая дублирующее объявление без версии), `slf4j-api`, `logback-classic`, `HikariCP`, `okhttp` при наличии в BOM, и удалить `dependency-reduced-pom.xml` из корня. Verify: `mvn -q clean package` проходит без предупреждений о дублирующихся зависимостях, `dependency-reduced-pom.xml` отсутствует.
- [x] 1.3 Добавить `spring-boot-starter-test` (scope `test`) и тест валидации конфигурации: `@Validated` отклоняет пустой `BOT_TOKEN`, а порт прокси вне 1..65535 при заданном `BOT_PROXY_HOST`. Verify: `mvn -q test` — тест падает на невалидном наборе и проходит на валидном.

## 2. Конфигурация из переменных окружения

- [x] 2.1 Добавить `BotProperties` (`bot.token`, `bot.db.{url,user,password}`, `bot.ai.key`, `bot.proxy.{type,host,port,user,pass}`) с `@ConfigurationProperties`, `@Validated`, `@NotBlank` на обязательных полях и `@AssertTrue isPortUsable()` в записи `Proxy` (порт `Integer`, пустой `BOT_PROXY_HOST` — прокси нет). Verify: тест из 1.3 зелёный, `mvn -q compile` проходит.
- [x] 2.2 Заменить `DataSourceConfig` на бин `DataSource` через `DataSourceBuilder` из `BotProperties`; удалить статическое поле соединения. Verify: локальный запуск с заполненными `BOT_DB_*` логирует успешную инициализацию пула Hikari, запросы к БД выполняются.
- [x] 2.3 Перевести `Main` в `@SpringBootApplication @ConfigurationPropertiesScan` с `SpringApplication.run`, удалить `resolveParameter` и `System.exit(1)`. Verify: с полным набором переменных бот стартует; при удалённом `BOT_TOKEN` процесс завершается с ненулевым кодом и сообщением, содержащим имя переменной, без подключения к БД и Telegram.

## 3. Компоненты в контексте

- [x] 3.1 Сделать `TelegramBot` компонентом с внедрением через конструктор (`BotProperties`, `DefaultBotOptions`), убрать создание `UsersService`/`ChatsService`/`CockSizeGame`/`PidorGame`/`HoroscopeService`/`AiService` в конструкторе. Verify: `mvn -q compile` проходит, `grep "new .*Service\|new .*Game" src/main/java/org/example/TelegramBot.java` не находит совпадений.
- [x] 3.2 Убрать создание зависимостей в полях в `CockSize/CockSizeService.java`, `CockSize/CockSizeGame.java`, `Horoscope/HoroscopeService.java`, `PidorGame/PidorGame.java`, `PidorGame/PidorGameRepository.java`, `AiChat/AiService.java`, `AiChat/DeepSeekClient.java`, `AiChat/ChatMessages.java`; классы сделать компонентами с конструкторным внедрением. Verify: `grep -rn "private final .* = new " src/main/java` не находит совпадений, `mvn -q compile` проходит. Уточнение: `ChatMessages` — состояние одного чата, поэтому остаётся объектом, создаваемым `AiService`, но получает `SettingsService` и `UsersService` в конструкторе.
- [x] 3.3 Объявить бины `DefaultBotOptions` (прокси из `bot.proxy.*`, включая `http(s).proxy*`/`socksProxy*` системные свойства и логирование выбранного режима) и `TelegramBotsApi`. Verify: при пустом `BOT_PROXY_HOST` бин возвращает непустые опции и логирует «прокси не задан»; при заданных `BOT_PROXY_HOST/PORT` логирует «Прокси host:port (HTTP)», и запрос `/ai` идёт через прокси; ни в одной ветке бин не возвращает `null`. Проверка `/ai` через прокси требует живого бота — см. 6.1.

## 4. Жизненный цикл и остановка

- [x] 4.1 Реализовать `SmartLifecycle`: в `start()` — прежний цикл трёх попыток `registerBot` с логированием номера попытки и сохранением возвращённого `BotSession`, в `stop()` — `BotSession.stop()`. Verify: при недоступном Telegram в лог попадают три попытки и процесс завершается с ненулевым кодом; при успешной регистрации в логе есть «Бот успешно запущен на попытке №N».
- [ ] 4.2 Проверить остановку по SIGTERM локально. Verify: после `Ctrl+C`/`taskkill` (SIGTERM) процесс завершается в пределах 20 секунд, в логе нет ошибок, и повторный запуск регистрируется без `409 Conflict`. На Windows `java.exe` не получает SIGTERM, поэтому проверка выполняется на VPS (6.2); путь `stop()` покрыт `BotLifecycleTest.stopsPollingOnShutdown`.

## 5. Локальный запуск и документация

- [x] 5.1 Перевести `.vscode/launch.json` с блока `args` на блок `env` с теми же `${env:...}` для обеих конфигураций («Бот (с прокси)», «Бот (без прокси)»); файл остаётся вне git. Verify: запуск из Run and Debug поднимает бота в обоих режимах, литеральных секретов в файле нет. Файла в рабочем каталоге не было — создан заново с двумя конфигурациями на `${env:...}`.
- [x] 5.2 Обновить раздел запуска в `README.md`: конфигурация только через переменные окружения `BOT_*`, позиционные аргументы больше не поддерживаются. Verify: команда из README выполняется как написано и поднимает бота.

## 6. Интеграционная проверка

- [ ] 6.1 Проверить команды после миграции в тестовом чате: `/ai`, `/summary`, `/pidor_*`, `/cocksize`, `/horoscope_today`. Verify: ответы и поведение совпадают с версией до миграции, ошибок в логе нет.
- [x] 6.2 Задеплоить на VPS без правок `deploy/telegram-bot.service` и `.github/workflows/deploy.yml`. Verify: `systemctl is-active telegram-bot` — `active`, `journalctl -u telegram-bot -n 50` без ошибок, `systemctl restart telegram-bot` проходит без конфликта сессии и без висящего long polling. Выполнено прогоном 41327780567 (`75a0194`): рестарт юнита прошёл, новый jar поднялся, Hikari подключился, «Бот успешно запущен на попытке №1», `systemctl is-active` → `active`.
- [ ] 6.3 Проверить остановку по SIGTERM уже на новой версии: во время деплоя останавливался предыдущий билд, поэтому `BotLifecycle.stop()` на VPS ещё не исполнялся. Verify: `systemctl restart telegram-bot` на новом билде — в `journalctl` есть «Останавливаю опрос обновлений Telegram» и «Опрос обновлений Telegram остановлен» (или предупреждение о таймауте сессии), нет `Failed with result 'timeout'`, следующий старт без `409 Conflict`. Первый замер (2026-11-01 18:10) выявил зависание `BotSession.stop()`: 20 с без строк в логе и SIGKILL; `stop()` ограничен 5 с, callback фазы отдаётся в `finally`, добавлен `spring.lifecycle.timeout-per-shutdown-phase=10s`, покрыто тестами `runsShutdownCallbackEvenWhenSessionStopFails` и `doesNotBlockShutdownWhenSessionStopHangs`.

## Workflow follow-up

- Деплой происходит автоматически при push в `main` и перезапускает живого бота — согласовать момент с пользователем перед пушем.
- После проверки на VPS синхронизировать дельты в `openspec/specs/runtime-configuration` и `openspec/specs/application-bootstrap`, затем перенести изменение в `openspec/changes/archive/2026-11-01-migrate-to-spring-boot/`.
- Закрыть issue #39 в GitHub после успешного деплоя.
