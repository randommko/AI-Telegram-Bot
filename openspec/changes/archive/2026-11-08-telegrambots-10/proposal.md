# Proposal

## Why

Бот работает на `org.telegram:telegrambots` 6.5.0 — линии, которая закончилась версией 6.9.7.1 (февраль 2024). Она не знает Bot API новее 7.0 и не позволяет задать `allowed_updates` при long polling, поэтому бот не сможет получать чужие реакции и пользоваться возможностями Bot API 8–10. Одновременно выяснилось, что поддержка прокси в приложении не нужна: в проде `BOT_PROXY_*` пусты, а Telegram доступен через маршруты OpenVPN-клиента `tg-relay` на самом сервере.

## What Changes

- **BREAKING (зависимости)**: библиотека поднимается до 10.3.0 (Bot API 10.3) — последней версии на Maven Central, требует Java 17, как и проект. Агрегатного артефакта `telegrambots` в этой линии нет: бот получает `telegrambots-longpolling` и `telegrambots-client`, core — только `telegrambots-meta`.
- **BREAKING (код)**: `extends TelegramLongPollingBot` заменяется на `LongPollingSingleThreadUpdateConsumer`; `TelegramBotsApi(DefaultBotSession.class).registerBot(bot)` — на `TelegramBotsLongPollingApplication.registerBot(token, consumer)`; `MessageSender` работает с `TelegramClient`; `objects.Message` и `objects.Chat` переезжают в `objects.message` и `objects.chat`.
- Прокси удаляется из приложения целиком: переменные `BOT_PROXY_*`, запись `Proxy` и её валидация в `BotProperties`, бин `DefaultBotOptions` с прокси и системные свойства JVM для `java.net.http`. Доступ к Telegram обеспечивают маршруты ОС на сервере и режим TUN при локальном запуске.
- `deploy-bot.yml` и `deploy/bot.env.example` перестают передавать `BOT_PROXY_*`; README бота и админки теряют упоминания прокси, раздел README про прокси становится описанием доступа к Telegram.
- Поведение обработки сообщений не меняется: по-прежнему три попытки регистрации, остановка в бюджете `TimeoutStopSec`, отправка через свой пул потоков.

**Вне объёма:** постановка и чтение реакций (отдельное изменение — сначала нужно решить, что бот с ними делает), переход на Spring-стартер библиотеки, изменение текстов ответов бота, миграции базы.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `runtime-configuration`: удаляется требование «Подключение к Telegram Bot API через конфигурируемый прокси», удаляются два сценария проверки конфигурации про прокси, из требования о переменных окружения убирается упоминание прокси у админки; добавляется требование «Прямое подключение к Telegram Bot API».

## Impact

- `bot/pom.xml`, `core/pom.xml` — артефакты и версия библиотеки.
- `bot/src/main/java/org/example/bot/` — `TelegramBot`, `MessageSender`, `BotLifecycle`, `config/BotInfrastructureConfig`, `CallbackDispatcher`, `CommandDispatcher`, `UserChatManager`, `aichat/AiService`.
- `core/src/main/java/org/example/core/` — `config/BotProperties`, `chats/ChatsService`, `chats/ChatsRepository`.
- Тесты: `BotLifecycleTest`, `MessageSenderTest`, `CommandDispatcherTest`, `CallbackDispatcherTest`, `KeyboardBuilderTest`, `AiServiceTest`, `core/config/BotPropertiesBindingTest`.
- Деплой и документация: `.github/workflows/deploy-bot.yml`, `deploy/bot.env.example`, `README.md`, `bot/README.md`, `admin/README.md`.
- Вне репозитория: секреты `BOT_PROXY_*` в GitHub Actions можно удалить после деплоя.
