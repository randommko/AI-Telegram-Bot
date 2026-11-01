# Proposal

## Why

На текущей сети `api.telegram.org` недоступен напрямую: TCP-подключение к порту 443 зависает по таймауту, поэтому после удаления прокси бот не может зарегистрироваться в Telegram (`registerBot` падает с `Connection timed out`). Нужна возможность подключаться к Telegram Bot API через прокси-сервер (HTTP/SOCKS5) либо работать через системный VPN — конфигурируемо, без хардкода в коде.

## What Changes

- Добавлена поддержка подключения к Telegram Bot API через HTTP- или SOCKS5-прокси, задаваемый переменными окружения `BOT_PROXY_TYPE`, `BOT_PROXY_HOST`, `BOT_PROXY_PORT`, `BOT_PROXY_USER`, `BOT_PROXY_PASS`.
- Прокси опционален: если не задан — прямое подключение (в том числе через системный VPN/TUN).
- Прокси применяется на двух уровнях: `DefaultBotOptions` (для telegrambots / Apache HttpClient) и системные свойства JVM (для `java.net.http.HttpClient` — DeepSeek и гороскоп).
- **BREAKING** (спекация): требование «Прямое подключение к Telegram Bot API» (запрет прокси) заменяется на «Подключение к Telegram Bot API через конфигурируемый прокси».
- CI/CD: прокси-параметры можно передавать на VPS через GitHub Secrets как переменные окружения.

## Capabilities

### New Capabilities

<!-- Новых capability нет. -->

### Modified Capabilities

- `runtime-configuration`: требование «Прямое подключение к Telegram Bot API» (запрет прокси) заменяется на подключение через конфигурируемый HTTP/SOCKS5-прокси с опциональным прямым режимом.

## Impact

- Код: `Main.java` (разбор env-переменных прокси, `DefaultBotOptions`, системные свойства JVM), `TelegramBot.java` (снова принимает `DefaultBotOptions`).
- CI/CD: `.github/workflows/deploy.yml` (передача прокси-env из секретов, опционально).
- Документация: README / инструкция по локальному запуску и деплою.
- Внешние системы: Telegram Bot API через прокси; локальный HTTP-прокси VPN (`127.0.0.1:12334`) и/или SOCKS5 на VPS.
