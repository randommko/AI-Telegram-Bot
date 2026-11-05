# Миграции

Схемы разделены по владельцу данных. Порядок наката важен: `shared` создаётся внешними скриптами `migrations-ops`, затем `config`, `ai`, `configurator`.

| Каталог | Что создаёт | От чего зависит |
|---|---|---|
| `config` | персоны, алиасы, привязки, справочники эмоций и памяти, `bot_settings` | `shared` |
| `ai` | история переписки, память, эмоции персоны, обиды, подсказки, `provider_key_status` | `config`, `shared` |
| `configurator` | учётки модераторов, выданные чаты | `shared` |

`shared` (`tg_users`, `tg_chats`) и разовый перенос строк из `public` живут вне репозитория — в `migrations-ops`. Игровые таблицы `pidor_*` и `cocksize_*` в этом репозитории не создаются: они переехали в `Fun-Telegram-Bot` (`fun`).

## Накат

```
psql "$BOT_DB_URL" -f migrations/config/001_schema.sql
psql "$BOT_DB_URL" -f migrations/config/002_seed.sql
psql "$BOT_DB_URL" -f migrations/ai/001_schema.sql
psql "$BOT_DB_URL" -f migrations/ai/002_seed.sql
psql "$BOT_DB_URL" -f migrations/configurator/001_schema.sql
```

Все файлы идемпотентны: `CREATE ... IF NOT EXISTS`, `ON CONFLICT DO NOTHING`. Повторный накат не меняет уже сохранённые значения настроек.

## Чего здесь нет

- `admin_changes` — журнал удалён, таблица не создаётся.
- `persona_state` — старая таблица настроения, никем не читалась.
- Шаблоны входа и выхода персоны — удалены вместе с отправкой сообщений в Telegram.
- Роли и гранты — в `migrations-ops/006_roles.sql` (`ai_user`, `admin_user`, `fun_user`).

Проверка наката и повторного наката выполняется на сервере: на машине разработки нет `psql`.
