# Spec Delta

## REMOVED Requirements

### Requirement: Прямое подключение к Telegram Bot API

**Reason**: На практике Telegram недоступен напрямую из ряда сетей (TCP-подключение к порту 443 зависает по таймауту), поэтому требование «MUST NOT требовать прокси» невыполнимо.

**Migration**: Использовать новое требование «Подключение к Telegram Bot API через конфигурируемый прокси» (HTTP/SOCKS5 через переменные окружения).

## ADDED Requirements

### Requirement: Подключение к Telegram Bot API через конфигурируемый прокси

Система SHALL подключаться к Telegram Bot API напрямую либо через HTTP- или SOCKS5-прокси, заданный переменными окружения `BOT_PROXY_TYPE`, `BOT_PROXY_HOST`, `BOT_PROXY_PORT`, `BOT_PROXY_USER`, `BOT_PROXY_PASS`. Прокси опционален: без него используется прямое подключение, в том числе через системный VPN.

#### Scenario: Прямое подключение без прокси

- **WHEN** переменная `BOT_PROXY_HOST` не задана
- **THEN** бот подключается к Telegram API напрямую (в том числе через системный VPN)

#### Scenario: Подключение через HTTP-прокси

- **WHEN** `BOT_PROXY_TYPE=HTTP` и заданы `BOT_PROXY_HOST` и `BOT_PROXY_PORT`
- **THEN** бот направляет запросы к Telegram API через HTTP-прокси

#### Scenario: Подключение через SOCKS5-прокси

- **WHEN** `BOT_PROXY_TYPE=SOCKS5` и заданы `BOT_PROXY_HOST` и `BOT_PROXY_PORT`
- **THEN** бот направляет запросы к Telegram API через SOCKS5-прокси

#### Scenario: Прокси с аутентификацией

- **WHEN** заданы `BOT_PROXY_USER` и `BOT_PROXY_PASS`
- **THEN** при подключении к прокси используется аутентификация
