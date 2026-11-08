# Tasks

Работа идёт в ветке `feat/telegrambots-10`; Maven запускается полным путём `C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd`.

## 1. Зависимости и core

- [x] 1.1 Поднять библиотеку в `bot/pom.xml` (`telegrambots-longpolling` + `telegrambots-client` 10.3.0) и в `core/pom.xml` (`telegrambots-meta` 10.3.0). Проверка: `mvn.cmd -q -DskipTests compile` скачивает новые артефакты и проходит.
- [x] 1.2 Убрать из `BotProperties` запись `Proxy`, метод `hasProxy()` и валидацию порта прокси; перевести импорты `Chat` в `ChatsService` и `ChatsRepository` на `objects.chat.Chat`. Попутно `UsersRepository` собирает `User` билдером: в 10.x подходящего конструктора нет. Проверка: `mvn.cmd -pl core -am compile`.
- [x] 1.3 Удалить из `BotPropertiesBindingTest` тесты `rejectsProxyPortOutOfRange` и `acceptsEmptyProxyPortWhenHostIsAbsent`, добавить тест на некорректный `BOT_ADMIN_PORT`, если его нет. Проверка: `mvn.cmd -pl core -am test` — зелёный.

## 2. Транспорт бота

- [x] 2.1 `BotInfrastructureConfig`: удалить бин `DefaultBotOptions` с прокси и бин `TelegramBotsApi`; добавить бины `TelegramBotsLongPollingApplication` и `TelegramClient` (`OkHttpTelegramClient` с токеном из `BotProperties`). Проверка: контекст бота поднимается в тестах, `mvn.cmd -pl bot -am test` проходит компиляцию.
- [x] 2.2 `TelegramBot`: реализовать `LongPollingSingleThreadUpdateConsumer.consume(Update)`, убрать наследование `TelegramLongPollingBot`, `getBotToken()`/`getBotUsername()` и `DefaultBotOptions`; проверка токена при создании бина сохраняется. Проверка: `mvn.cmd -pl bot -am compile`.
- [x] 2.3 `MessageSender` перевести на `TelegramClient` без `ObjectProvider` и `getBot()`; `CallbackDispatcher` зовёт `messageSender.getClient()`; `MessageSenderTest` мокает `TelegramClient`. Проверка: `mvn.cmd -pl bot -am test -Dtest=MessageSenderTest,CallbackDispatcherTest`.
- [x] 2.4 `BotLifecycle`: заменить `TelegramBotsApi` на `TelegramBotsLongPollingApplication`, сессию — на `longpolling.BotSession`; сохранить три попытки и остановку с таймаутом 5 с, при неудачной попытке закрывать созданную сессию. Проверка: `mvn.cmd -pl bot -am test -Dtest=BotLifecycleTest` — успешная регистрация, три неудачи, таймаут остановки.
- [x] 2.5 Перевести импорты `Message`/`Chat` в `CommandDispatcher`, `UserChatManager`, `AiService` и поправить моки в `CommandDispatcherTest`, `CallbackDispatcherTest`, `KeyboardBuilderTest`, `AiServiceTest` на `TelegramClient` и новые пути DTO. Попутно клавиатуры собраны через `InlineKeyboardRow` и билдеры (в 10.x нет пустых конструкторов), а `BotProperties` в тестах создаётся из пяти аргументов. Проверка: `mvn.cmd -pl bot -am test` — все тесты бота зелёные.

## 3. Деплой и документация без прокси

- [x] 3.1 Убрать строки `BOT_PROXY_*` из `.github/workflows/deploy-bot.yml` и блок прокси из `deploy/bot.env.example`. Проверка: `git grep -n "BOT_PROXY"` находит только архивные спеки, основную спеку (она обновится при архивации) и это изменение.
- [x] 3.2 Переписать раздел README о прокси в описание доступа к Telegram (в проде маршруты VPN, локально TUN), убрать строку `BOT_PROXY_*` из `bot/README.md` и упоминание прокси из `admin/README.md`. Проверка: команды из README выполняются как написано, упоминаний прокси в актуальных README нет.

## 4. Проверка и выкатка

- [x] 4.1 Прогнать весь набор: `mvn.cmd clean test` — 475 тестов без падений (477 минус два удалённых теста прокси), сборка двух jar.
- [x] 4.2 Слить ветку в `main` и дождаться `Deploy bot` со `success`; сверить, что деплой запустился на SHA слияния. Проверка: `gh run list --json databaseId,workflowName,headSha,conclusion` — запуск 42489639749 на `eb98fa9`, `success`.
- [x] 4.3 По журналу сервера убедиться, что бот запустился с первой попытки, строки про прокси больше нет, ошибок `TelegramApiException` нет. Проверка: `journalctl -u telegram-bot --no-pager -n 50` — «Бот успешно запущен на попытке №1», ошибок за 10 минут после старта нет.
- [x] 4.4 Смок в реальном чате: команда, обычный ответ с персоной, кнопка с callback. Проверка: подтверждение пользователя в чате — «Проверил, всё работает» (2026-11-08).
- [x] 4.5 Удалить секреты `BOT_PROXY_*` из GitHub Actions. Проверка: `gh secret list` их не показывает — в репозитории таких секретов и не было.

## Workflow follow-up

- Архивировать изменение после деплоя и смока; `retire_capabilities` не нужен, в дельте есть ADDED-требования.
- Проверить, что основная спека `runtime-configuration` после архивации не содержит требований и сценариев про прокси.
