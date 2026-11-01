# Tasks

## 1. Настройка прокси в коде

- [x] 1.1 Добавить в `Main` разбор env-переменных `BOT_PROXY_TYPE`, `BOT_PROXY_HOST`, `BOT_PROXY_PORT`, `BOT_PROXY_USER`, `BOT_PROXY_PASS` (тип по умолчанию `HTTP` при заданном host). Verify: при пустом `BOT_PROXY_HOST` прокси не настраивается, при заданном — параметры логируются без секретов.
- [x] 1.2 Собирать `DefaultBotOptions` с `ProxyType.HTTP`/`SOCKS5` при заданном прокси и передавать в `TelegramBot`; без прокси — не задавать (прямое подключение). Verify: `TelegramBot` получает опции; при `null` — `super()`.
- [x] 1.3 Выставить системные свойства для `java.net.http.HttpClient` (`https.proxyHost/Port` для HTTP, `socksProxyHost/Port` для SOCKS5) и `Authenticator` при логине. Verify: `DeepSeekClient`/`HoroscopeService` маршрутизируются через прокси (проверка по успешному запросу через прокси).
- [x] 1.4 Изменить конструктор `TelegramBot` на `(String botToken, String aiToken, DefaultBotOptions options)` с `super(options)`/`super()` и обновить вызов в `Main`. Verify: `mvn -q compile` проходит без ошибок.

## 2. Документация

- [x] 2.1 Обновить README/инструкцию: запуск с HTTP-прокси VPN (`BOT_PROXY_TYPE=HTTP BOT_PROXY_HOST=127.0.0.1 BOT_PROXY_PORT=12334`) и запуск без прокси (системный VPN в режиме TUN), а также SOCKS5-вариант. Verify: команды из документации запускаются как написано.

## 3. CI/CD

- [x] 3.1 Добавить в `.github/workflows/deploy.yml` проброс прокси-параметров из GitHub Secrets как env-переменных (только если соответствующие секреты заданы). Verify: чтение `deploy.yml` показывает `BOT_PROXY_*`, подставляемые из secrets.

## 4. Интеграционная проверка

- [x] 4.1 Собрать `mvn clean package` и запустить локально с HTTP-прокси `127.0.0.1:12334`. Verify: лог «Бот успешно запущен», команда `/bot_info` отвечает.

## Workflow follow-up

- 4.2 (запуск без прокси через TUN) отложена по решению пользователя от 2026-11-01: TUN-режим Hiddify поднимает адаптер и default-маршрут, но прямой трафик не проходит (таймаут даже на example.com). Рабочий путь — HTTP-прокси.
- 4.3 (SOCKS5-режим) не проверялась: SOCKS5-эндпоинт не предоставлен (условие «если доступен» не выполнено).
- Архив изменения после ревью и успешного деплоя.
- Настроить прокси-секреты (`BOT_PROXY_*`) в GitHub для VPS.
