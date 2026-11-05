# Модуль bot

Headless-приложение AI-чата: long polling Telegram, команды `/ai` и `/clear`, персона, память, эмоции.
Веб-слой, Spring Security и Thymeleaf сюда не входят — это отдельный модуль `admin`.

## Сборка и запуск

Из корня репозитория:

```
mvn -pl bot -am package
java -jar bot/target/telegram-bot-1.0.jar
```

Бот не открывает HTTP-порт. Остановка — SIGTERM, в пределах `TimeoutStopSec=20`.

## Переменные окружения

| Переменная | Значение |
|---|---|
| `BOT_TOKEN` | токен AI-бота |
| `BOT_DB_URL` | JDBC-URL базы |
| `BOT_DB_USER` | роль `ai_user` |
| `BOT_DB_PASSWORD` | пароль роли |
| `BOT_AI_KEY_<ID>` | ключи провайдеров; ни один не обязателен |
| `BOT_PROXY_*` | необязательный прокси |

При старте бот публикует в `ai.provider_key_status` только признак наличия ключа: админка читает его и не получает сами ключи.

Переменные `BOT_ADMIN_*` бот не читает.
