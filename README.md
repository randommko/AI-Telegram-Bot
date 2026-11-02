Бот для Telegram.

Команды:

- `/cocksize` — Измерить достоинство
- `/pidor_start` — Выбрать пидора дня
- `/horoscope_today` — Гороскоп
- `/pidor_reg` — Вступить в ряды пидоров
- `/pidor_stats` — Статистика пидоров
- `/ai` — Спроси ИИ
- `/clear` — Обнулить память AI (с подтверждением)
- `/summary` — Пересказ последних 100 сообщений

## Запуск

Требуется JDK 17. Сборка:

```
mvn clean package
```

Конфигурация передаётся только переменными окружения — позиционные аргументы командной строки не поддерживаются (они видны в `ps` любому локальному пользователю):

| Переменная | Значение |
|---|---|
| `BOT_TOKEN` | токен бота |
| `BOT_DB_URL` | JDBC-URL базы |
| `BOT_DB_USER` | пользователь базы |
| `BOT_DB_PASSWORD` | пароль базы |
| `BOT_AI_KEY` | ключ ИИ |

Windows (cmd):

```
set BOT_TOKEN=<token>
set BOT_DB_URL=jdbc:postgresql://host:5432/db
set BOT_DB_USER=<dbUser>
set BOT_DB_PASSWORD=<dbPassword>
set BOT_AI_KEY=<aiKey>
java -jar target/telegram-bot-1.0.jar
```

Linux:

```
BOT_TOKEN=<token> BOT_DB_URL=jdbc:postgresql://host:5432/db \
BOT_DB_USER=<dbUser> BOT_DB_PASSWORD=<dbPassword> BOT_AI_KEY=<aiKey> \
java -jar target/telegram-bot-1.0.jar
```

Обязательные переменные проверяются при старте: если значение пустое или не задано, приложение завершается с ненулевым кодом и сообщением, в котором указано имя переменной, не подключаясь к базе и Telegram.

## Подключение к Telegram через прокси или VPN

Прокси опционален и задаётся переменными окружения. Если `BOT_PROXY_HOST` не задан — бот подключается к Telegram напрямую (это работает, если включён системный VPN в режиме TUN).

Переменные:

| Переменная | Описание |
|---|---|
| `BOT_PROXY_TYPE` | `HTTP` (по умолчанию) или `SOCKS5` |
| `BOT_PROXY_HOST` | Хост прокси; пусто/не задано — прямое подключение |
| `BOT_PROXY_PORT` | Порт прокси |
| `BOT_PROXY_USER` | Логин прокси (опционально) |
| `BOT_PROXY_PASS` | Пароль прокси (опционально) |

### Без прокси (системный VPN в режиме TUN)

```
java -jar target/telegram-bot-1.0.jar
```

### HTTP-прокси (например, локальный прокси VPN-клиента)

Windows (cmd):

```
set BOT_PROXY_TYPE=HTTP
set BOT_PROXY_HOST=127.0.0.1
set BOT_PROXY_PORT=12334
java -jar target/telegram-bot-1.0.jar
```

Linux:

```
BOT_PROXY_TYPE=HTTP BOT_PROXY_HOST=127.0.0.1 BOT_PROXY_PORT=12334 \
java -jar target/telegram-bot-1.0.jar
```

### SOCKS5-прокси (например, на VPS)

```
BOT_PROXY_TYPE=SOCKS5 \
BOT_PROXY_HOST=<socks-host> \
BOT_PROXY_PORT=<socks-port> \
BOT_PROXY_USER=<login> \
BOT_PROXY_PASS=<password> \
java -jar target/telegram-bot-1.0.jar
```

В CI/CD эти же переменные передаются из GitHub Secrets.
