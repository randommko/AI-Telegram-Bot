# Proposal

## Why

С 24 ноября 2026 AI-бот молчал, хотя служба `telegram-bot` оставалась `active`: исходящий туннель до Telegram на VPS перестал подниматься, и запросы `getUpdates` падали с `SocketTimeoutException: Connect timed out`. После починки туннеля 27 ноября выяснилось второе последствие: библиотека `telegrambots-longpolling` увеличивает паузу между повторами экспоненциально и после 15 минут сплошных ошибок возвращает 900000 мс. Бот получил последнюю ошибку в 06:34:48, а следующую попытку поставил только на 06:49:48 — то есть выглядел мёртвым ещё четверть часа после восстановления сети, и его пришлось перезапускать вручную.

## What Changes

- В `BotInfrastructureConfig` приложение long polling создаётся с собственным `BackOff`: `ExponentialBackOff.Builder().setMaxElapsedTimeMillis(180_000)`.
- Потолок паузы между повторами `getUpdates` становится 3 минуты вместо 15. Рост интервала от 500 мс до 60 с и сброс паузы после успешного запроса сохраняются — это поведение библиотеки.
- Три первые зависимости конструктора (`ObjectMapper`, `OkHttpClient`, `ScheduledExecutorService`) передаются значениями, повторяющими умолчания библиотеки: другого способа добраться до `BackOff` у `TelegramBotsLongPollingApplication` нет.

### Вне объёма

- Инфраструктура VPS: содержимое `/etc/openvpn/tg-relay.conf`, `/etc/hosts` и `/etc/resolv.conf` живёт на сервере, а не в репозитории, и в этом изменении только описано.
- Fun-бот: он использует старый `TelegramBotsApi(DefaultBotSession.class)`, где своя логика пауз; правка его не касается.
- Два падающих теста `CommandDispatcherTest.modelInfoShowsCostForPricedModel` и `modelInfoOmitsCostForModelWithoutPrice` (`UnnecessaryStubbing`): они воспроизводятся на `main` без этой правки и чинятся отдельно.

## Capabilities

### New Capabilities

Нет: меняется поведение уже описанного жизненного цикла бота.

### Modified Capabilities

- `application-bootstrap`: добавляется требование о предельной паузе между повторами `getUpdates` и её сбросе после успешного запроса.

## Impact

- Модуль `bot`: класс `org.example.bot.config.BotInfrastructureConfig`, бин `telegramBotsApplication`.
- Поведение после сетевых сбоев: бот возвращается к опросу максимум через 3 минуты после восстановления связи, ручной перезапуск больше не нужен.
- Пользовательские сценарии, база, админка и формат ответов не меняются.
