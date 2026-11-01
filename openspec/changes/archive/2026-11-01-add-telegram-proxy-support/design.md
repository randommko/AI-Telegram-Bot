# Design

## Context

См. proposal.md — Why. Текущее состояние: `Main` читает 5 аргументов CLI, `TelegramBot` вызывается как `new TelegramBot(botToken, aiToken)` с `super()` и без прокси. Тест показал, что `-Dhttps.proxyHost/-Dhttps.proxyPort` **не помогают** telegrambots: её Apache HttpClient игнорирует системные свойства и лезет напрямую (`DefaultHttpClientConnectionOperator.connect → api.telegram.org:443 → timed out`). Прокси библиотеке задаётся только через `DefaultBotOptions`.

## Goals / Non-Goals

**Goals:**
- Подключаться к Telegram API через HTTP/SOCKS5-прокси, задаваемый окружением, либо напрямую.
- Один и тот же механизм работает локально (HTTP-прокси VPN) и на VPS (SOCKS5).
- Секреты прокси не попадают в аргументы процесса/репозиторий.

**Non-Goals:**
- Не добавляем UI/команды управления прокси из Telegram.
- Не меняем arg-контракт из 5 аргументов (прокси — отдельный, опциональный канал).
- Не меняем поведение DeepSeek/гороскопа, кроме маршрутизации их `HttpClient` через прокси.

## Decisions

### D1: Конфигурация прокси — переменные окружения

Читаем `BOT_PROXY_TYPE` (`HTTP`|`SOCKS5`), `BOT_PROXY_HOST`, `BOT_PROXY_PORT`, `BOT_PROXY_USER`, `BOT_PROXY_PASS`. Если `BOT_PROXY_HOST` пуст/не задан — прямое подключение.

**Альтернативы:** дополнительные CLI-аргументы (громоздко, секреты видны в `ps`); `config.json` (ранее удалён). Env удобны для CI/CD (GitHub Secrets → env) и для локального запуска.

### D2: Прокси на двух уровнях

1. `DefaultBotOptions` с `ProxyType.HTTP`/`SOCKS5` + host/port (и credentials) → передаём в `TelegramBot` (telegrambots/Apache HttpClient).
2. Системные свойства JVM → для `java.net.http.HttpClient` (`DeepSeekClient`, `HoroscopeService`):
   - HTTP: `https.proxyHost`/`https.proxyPort` (+ `Authenticator` при логине);
   - SOCKS5: `socksProxyHost`/`socksProxyPort` (+ `Authenticator`).

Причина: разные HTTP-стеки, разный способ задания прокси; проверено тестом, что только `-D`-флагов telegrambots недостаточно.

### D3: Прямое подключение как режим по умолчанию

Без `BOT_PROXY_HOST` — `DefaultBotOptions` не устанавливает прокси, системные свойства не трогаем. Это покрывает системный VPN в режиме TUN, когда прокси не нужен.

### D4: `TelegramBot` снова принимает опциональные `DefaultBotOptions`

Сигнатура: `TelegramBot(String botToken, String aiToken, DefaultBotOptions options)`; при `null` — `super()` (прямое подключение). `Main` собирает `options` только если прокси задан.

### D5: Тип прокси по умолчанию

Если `BOT_PROXY_HOST` задан, а `BOT_PROXY_TYPE` — нет, считаем `HTTP` (наиболее частый локальный кейс, как у VPN). Явный `SOCKS5` требует указания типа.

## Risks / Trade-offs

- **Локальный порт HTTP-прокси VPN меняется между сессиями** (`127.0.0.1:12334`) → параметры задаются окружением при запуске, не хардкодятся.
- **Секреты прокси в env** → на VPS хранить в GitHub Secrets; не логировать значения.
- **SOCKS5 и DNS**: часть SOCKS5-прокси требует удалённого резолва имён → при проблемах использовать `socksProxyVersion`/remote DNS на уровне прокси; отметить в инструкции.
- **На VPS Telegram может быть доступен напрямую** → прокси опционален, env просто не задаём.
- **Регресс прямого режима** → тест: запуск без env-переменных должен работать там, где Telegram доступен (например, при включённом TUN-VPN).

## Migration Plan

1. Реализовать разбор env и настройку `DefaultBotOptions`/системных свойств.
2. Локально: `BOT_PROXY_TYPE=HTTP`, `BOT_PROXY_HOST=127.0.0.1`, `BOT_PROXY_PORT=12334` (из VPN) → проверить старт бота.
3. VPS: добавить прокси-секреты и прокинуть их в `deploy.yml` как env.
4. Откат: убрать env-переменные → прямое подключение.

## Open Questions

- Точные параметры SOCKS5 на VPS (host/port/логин/пароль) пока неизвестны; задаются при деплое через секреты и не влияют на спек/подход.
