# Миграции

Набор самодостаточен: на чистой базе достаточно файлов этого каталога. Порядок важен, потому что `config`, `ai`, `configurator` ссылаются на `shared`, а роли выдаются на уже созданные таблицы.

| Каталог | Что создаёт | От чего зависит |
|---|---|---|
| `shared` | `tg_users`, `tg_chats` — общие таблицы Telegram | — |
| `config` | персоны, алиасы, привязки, справочники эмоций и памяти, каталог эмодзи реакций, `bot_settings` | `shared` |
| `ai` | история переписки, память, эмоции персоны, обиды, подсказки, `provider_key_status`, журнал реакций и отметка прочитанного | `config`, `shared` |
| `configurator` | учётки модераторов, выданные чаты | `shared` |
| `roles` | роли `ai_user` и `admin_user`, их права и права на будущие таблицы | все схемы |

`shared` и роли существуют в двух копиях — здесь и в `Fun-Telegram-Bot`. Это осознанное дублирование: каждый репозиторий раскатывается независимо, общий код и общие миграции между проектами не поддерживаются.

Игровые таблицы `pidor_*` и `cocksize_*` в этом репозитории не создаются: они принадлежат `Fun-Telegram-Bot`.

## Накат

```
psql "$BOT_DB_URL" -f migrations/shared/001_schema.sql
psql "$BOT_DB_URL" -f migrations/config/001_schema.sql
psql "$BOT_DB_URL" -f migrations/config/002_seed.sql
psql "$BOT_DB_URL" -f migrations/config/003_memory_decay.sql
psql "$BOT_DB_URL" -f migrations/config/004_memory_decay_floor.sql
psql "$BOT_DB_URL" -f migrations/config/005_reaction_emojis.sql
psql "$BOT_DB_URL" -f migrations/ai/001_schema.sql
psql "$BOT_DB_URL" -f migrations/ai/002_seed.sql
psql "$BOT_DB_URL" -f migrations/ai/003_message_reactions.sql
psql "$BOT_DB_URL" -f migrations/configurator/001_schema.sql
psql "$BOT_DB_URL" -f migrations/roles/001_roles.sql
```

В `roles/001_roles.sql` заменить `CHANGE_ME_AI` и `CHANGE_ME_ADMIN` на пароли перед запуском: в репозитории они не хранятся. Если роли уже созданы, блок `IF NOT EXISTS` их не тронет, а гранты применятся повторно.

Все файлы идемпотентны: `CREATE ... IF NOT EXISTS`, `ON CONFLICT DO NOTHING`. Повторный накат не меняет уже сохранённые значения настроек.

## Чего здесь нет

- `admin_changes` — журнал удалён, таблица не создаётся.
- `persona_state` — старая таблица настроения, никем не читалась.
- Шаблоны входа и выхода персоны — удалены вместе с отправкой сообщений в Telegram.

Проверка наката и повторного наката выполняется на сервере: на машине разработки нет `psql`.
