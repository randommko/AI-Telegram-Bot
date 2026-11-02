# Design

## Context

См. `proposal.md` — Why. Текущее состояние, важное для решений:

- `Main.main()` читает параметры через `Main.java:64` (`resolveParameter`: аргумент → env), валидирует «на пустоту», затем `configureProxy()` (`Main.java:76`), `DataSourceConfig.initialize` (`DataSourceConfig.java:18`), и цикл из трёх `registerBot` в `Main.java:51-70`.
- `TelegramBot` (`TelegramBot.java:31`) создаёт `UsersService`, `ChatsService`, `CockSizeGame`, `PidorGame`, `HoroscopeService`, `AiService` в конструкторе, а сервисы дополнительно создают друг друга в полях — 9 мест с `new` вне `Main`/`TelegramBot`.
- `configureProxy()` возвращает `DefaultBotOptions` **всегда** (ветку с `null` починил `d04a107`) и дополнительно выставляет `http(s).proxyHost` / `socksProxyHost`, потому что Apache HttpClient внутри telegrambots игнорирует JVM-свойства.
- Имена переменных зафиксированы инфраструктурой: `.github/workflows/deploy.yml` пишет `BOT_TOKEN`, `BOT_DB_URL`, `BOT_DB_USER`, `BOT_DB_PASSWORD`, `BOT_AI_KEY`, `BOT_PROXY_*` в `/etc/telegram-bot/bot.env`; юнит запускает `/usr/bin/java -jar /usr/bin/tg_bot/telegram-bot-1.0.jar` с `KillSignal=SIGTERM`, `TimeoutStopSec=20`, `Restart=always`, `StartLimitIntervalSec=0`.
- В telegrambots 6.5.0 (проверено `javap` по `telegrambots-meta-6.5.0.jar` и по исходникам `DefaultBotSession`) у `TelegramBotsApi` нет метода отмены регистрации, но `registerBot(LongPollingBot)` возвращает `BotSession` с `stop()`, а `DefaultBotSession.stop()` снимает флаг `running` и прерывает `readerThread`/`handlerThread`.

## Goals / Non-Goals

**Goals:**

- Конфигурация и её валидация до любых сетевых подключений.
- Единственный экземпляр каждого сервиса/репозитория, внедрение через конструктор.
- Сохранение текущего поведения бота, путей деплоя и env-файла.

**Non-Goals:**

- Переписывание игр, AI и обработчиков команд; изменение SQL-схемы или введение Flyway/Liquibase.
- Замена `telegrambots` на другую библиотеку или переход на webhook.
- Изменение `deploy/telegram-bot.service` и `.github/workflows/deploy.yml`.

## Decisions

### 1. Конфигурация: `@ConfigurationProperties` с вложенными записями

Релаксированный биндинг отображает `BOT_DB_URL` в `bot.db.url`, поэтому группы обязаны быть вложенными: `bot.token`, `bot.db.{url,user,password}`, `bot.ai.key`, `bot.proxy.{type,host,port,user,pass}`. Плоская запись (`dbUrl`) потребовала бы других имён переменных, а они зафиксированы в CI и юните.

Альтернативы: `@Value` по каждой переменной (нет группировки и валидации, 10 инъекций); переименовать env в `SPRING_DATASOURCE_*` (правки в CI и на сервере); `EnvironmentPostProcessor` для алиасов (лишний слой ради косметики).

`@ConfigurationPropertiesScan` на главном классе вместо `@EnableConfigurationProperties` — меньше ручных связей при добавлении групп.

### 2. Валидация: `@Validated` + `@NotBlank`, условный порт через `@AssertTrue`

`spring-boot-starter-validation` добавляется явно: без hibernate-validator `@NotBlank` не выполняется и ошибка остаётся незамеченной. Обязательные поля — `token`, `db.*`, `ai.key`. Порт прокси нельзя описать `@NotNull`, потому что прокси опционален; поэтому в записи `Proxy` объявляется метод `isPortUsable()` с `@AssertTrue`: `host` пуст → ок, `host` задан и `port` вне 1..65535 → конфигурация невалидна.

Пустой `BOT_PROXY_HOST` трактуется как «прокси не нужен», а не как ошибка: CI пишет в env-файл пустые строки, когда соответствующие секреты не заданы, и такой файл должен оставаться рабочим.

Порт объявляется как `Integer`, а не `int`: пустая строка в env не должна ломать биндинг.

Ошибка валидации прерывает старт (`ConfigurationPropertiesBindException`) — код возврата ненулевой, сообщение попадает в journalctl. Прежний `System.exit(1)` убирается: те же наблюдаемые последствия без ручного выхода.

### 3. Пул соединений: `DataSource` из свойств, а не `spring.datasource.*`

Автоконфигурация JDBC ожидает `spring.datasource.url|username|password`, у нас `BOT_DB_*`. Поэтому объявляется бин `DataSource` через `DataSourceBuilder.create().url(...).username(...).password(...)`: Hikari остаётся реализацией по умолчанию из стартера, ручной `new HikariDataSource` и статическое поле в `DataSourceConfig` исчезают.

Альтернатива — оставить `DataSourceConfig` и вызывать его из `@Bean` — сохранила бы статическое состояние и порядок инициализации, ради которого нет причин.

### 4. Telegram: бины `DefaultBotOptions`, `TelegramBotsApi`, `TelegramLongPollingBot`

`DefaultBotOptions` — бин, всегда возвращающий объект (ветка «прокси не задан» возвращает пустые опции, а не `null`). Установка `http(s).proxy*` / `socksProxy*` системных свойств остаётся внутри фабрики этого бина: она должна произойти один раз при старте, до первых обращений `java.net.http` из DeepSeek/гороскопа.

`TelegramBot` становится `@Component` с внедрением зависимостей через конструктор; `getBotUsername`/`getBotToken` продолжают читать значения из `BotProperties`.

Альтернатива — передать прокси в `HttpClient.newBuilder().proxy(...)` в AI- и гороскоп-клиентах — чище, но это переписывание логики клиентов, что выходит за рамки «меняем только каркас».

### 5. Старт/остановка: `SmartLifecycle` вокруг `registerBot`

`ApplicationRunner` не даёт хука остановки, поэтому выбирается `SmartLifecycle`: `start()` содержит прежний цикл трёх попыток и сохраняет логи «Бот успешно запущен на попытке №N», `stop()` останавливает `BotSession`.

Остановка ограничена по времени (5 с, вызов `BotSession.stop()` уходит в отдельный поток): `DefaultBotSession.stop()` синхронизирован и через переопределённый `interrupt()` закрывает Apache-клиент, пока reader висит в long polling, поэтому вызов может не вернуться. Кроме того `stop(Runnable callback)` переопределён с `try/finally` — фаза shutdown получает сигнал даже при исключении, — а `getPhase()` поднят к максимуму, чтобы бот останавливался раньше остальных бинов. В `application.properties` задан `spring.lifecycle.timeout-per-shutdown-phase=10s`: любое зависание укладывается в `TimeoutStopSec=20` юнита и видно в journalctl вместо SIGKILL.

Альтернативы: `spring-retry` (новая зависимость без выигрыша — попыток ровно три и они логируются по-своему); `@PreDestroy` в самом `TelegramBot` (бин не знает свою сессию, пришлось бы хранить её в статическом поле).

**Проверено на VPS 2026-11-01:** первый рестарт нового билда дал `Failed with result 'timeout'` и SIGKILL через 20 с без единой строки в логе приложения — `stop()` не дошёл до логирования, что подтверждает зависание внутри `BotSession.stop()`; отсюда ограничение по времени и переопределённый `stop(Runnable)`.

### 6. Сборка: `spring-boot-maven-plugin` вместо shade

`repackage` перезаписывает `target/telegram-bot-1.0.jar` исполняемым архивом (исходный остаётся `*.jar.original`), поэтому `scp target/telegram-bot-1.0.jar` в CI, путь в юните и имя файла не меняются. Явные версии Jackson/slf4j/logback/HikariCP снимаются — их даёт родитель; `org.telegram:telegrambots:6.5.0` остаётся закреплённой. `maven-jar-plugin` с `mainClass` больше не нужен: манифест формирует Boot. `dependency-reduced-pom.xml` — побочный продукт shade, удаляется.

`src/main/resources/logback.xml` сохраняется: файл на classpath имеет приоритет над дефолтами Boot, консольный аппендер устраивает systemd/journalctl.

## Risks / Trade-offs

- **Позиционные аргументы перестают работать** → локальный запуск из `.vscode/launch.json` (не в git) переводится с `args` на `env` с теми же `${env:...}`; в README/инструкциях по локальному запуску аргументы заменяются на переменные окружения.
- **Неверный `bot.env` = бесконечный рестарт** (`StartLimitIntervalSec=0` + `Restart=always`) → валидация даёт понятное сообщение в journalctl первым делом; лимит рестартов сознательно не вводится, чтобы не менять юнит, и выносится в задачи как проверяемое поведение.
- **Пустые `BOT_PROXY_*` из CI** → `host` пуст считается отсутствием прокси; при заданном хосте и пустом порте запуск падает с явной ошибкой (см. спеку `runtime-configuration`).
- **Порядок бинов и прокси-свойства** → настройка системных свойств выполняется в фабрике `DefaultBotOptions`, до создания HTTP-клиентов; в задачах — smoke-проверка `/ai` через прокси.
- **Дублирующееся объявление `jackson-databind` в `pom.xml`** (одна запись без версии) сейчас держится на слиянии Maven-деклараций → при переходе на родителя дубликаты удаляются, версия приходит из BOM.
- **Потеря поведения `configureProxy()` при рефакторинге** (исторический дефект `null` из `d04a107`) → бин обязан возвращать `DefaultBotOptions` в любом случае, это отдельный пункт проверки в задачах.
