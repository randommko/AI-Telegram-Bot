# Tasks

## 1. Схема и данные

- [x] 1.1 Добавить `created_by` в `config.personas` (`migrations/config/001_schema.sql`: колонка в `CREATE TABLE` и `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` со ссылкой на `shared.tg_users` и `ON DELETE SET NULL`). Verify: файл содержит обе строки, ссылка на `shared.tg_users(user_id)` на месте.
- [x] 1.2 Создать `configurator.moderator_personas` в `migrations/configurator/001_schema.sql` (tg_user_id, persona_id, granted_by, granted_at, PK по паре, каскад по персоне). Verify: `CREATE TABLE IF NOT EXISTS` и оба `REFERENCES` на месте.
- [x] 1.3 Добавить бэкфилл грантов в конец `migrations/configurator/001_schema.sql`: каждому модератору — персона, все чаты которой ему выданы, `ON CONFLICT DO NOTHING`. Verify: запрос идемпотентен, повторного наката не боится.
- [x] 1.4 Добавить константу `MODERATOR_PERSONAS_TABLE` в `core/.../settings/TablesDB.java`. Verify: имя таблицы совпадает с миграцией.

## 2. Правила доступа

- [x] 2.1 Переписать `PersonaRepository`: `findAllWithCounts(scope)` с предикатом «чат ИЛИ создал ИЛИ выдан», `personaAccess`, `editablePersonaIds(tgUserId)`; `createInChat(details, chatId, createdBy)` пишет `created_by`; удалить `permission` и `editablePersonaIds(chatIds)`. Verify: `grep` по `permission(` и `editablePersonaIds(` в `core`/`admin` не находит старых вызовов.
- [x] 2.2 Переписать `AdminAccess`: `canSeePersona` = чат ИЛИ создатель ИЛИ грант, `canEditPersona` = создатель ИЛИ грант, `visiblePersonaIds` — правимые, `personaScope` для списков; владелец без ограничений. Verify: тесты группы 2.4.
- [x] 2.3 Поправить вызовы в `AdminPersonaController`, `AdminChatController`, `AdminMemoryController` под новые сигнатуры; в «Чатах» на привязку предлагаются только правимые персоны; текст режима «только чтение» в `persona-form.html` говорит про выдачу владельцем. Verify: `mvn -pl admin -am compile` проходит.
- [x] 2.4 Переписать `AdminAccessTest` под новые правила: чат даёт просмотр, создатель правит, грант правит, чужое без чата не видно, владелец видит всех. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" -q -pl admin -am test -Dtest=AdminAccessTest` — зелёный.

## 3. Выдача в разделе «Модераторы»

- [x] 3.1 Добавить `ModeratorPersonaRepository`: чтение доступных персон с источником, выдача и отзыв доступа к персоне. Verify: методы используют `configurator.moderator_personas` и возвращают источник `выдан`/`создал`/`чат`.
- [x] 3.2 В `AdminAccountsController` добавить `POST /admin/moderators/{id}/personas` и `.../personas/{personaId}/delete`, отдавать в модель доступных персон учётки и полный список персон для выдачи. Verify: оба маршрута отвечают редиректом с `saved`/`error` и доступны только владельцу (правило цепочки безопасности).
- [x] 3.3 В `moderators.html` показать по каждой учётке список персон с источником, кнопку отзыва у выданных и форму выдачи (select персоны). Verify: страница собирается, форма отправляет `personaId`.
- [x] 3.4 Добавить тесты выдачи, отказа для несуществующей персоны и отзыва на моках. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" -q -pl admin -am test` — все тесты зелёные (172).

## 4. Документация

- [x] 4.1 Обновить `README.md`: правило «чат — просмотр, правит создатель или выданный доступ», выдача в разделе «Модераторы», доступ владельца. Verify: описание совпадает с дельтой спеки.
- [x] 4.2 Проверить изменение целиком. Verify: `openspec validate moderator-persona-access --type change --strict` — «is valid».

## 5. Выкатка

- [ ] 5.1 Собрать оба jar. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" clean package` — `BUILD SUCCESS`.
- [ ] 5.2 Накатить миграции на прод (нужна роль-владелец) и проверить, что таблица и колонка появились. Verify: `information_schema` показывает `config.personas.created_by` и `configurator.moderator_personas`, ACL на новую таблицу у `admin_user` = `arwd`.
- [ ] 5.3 Выкатить в `main` и дать пользователю пройти сценарий: у модератора его чат даёт просмотр персоны, выдача владельцем даёт правку, отзыв возвращает просмотр, созданная модератором персона правится без выдачи. Verify: пользователь подтверждает в интерфейсе.

## Workflow follow-up

- Закрыть #83 с итогом выкатки.
- Архивировать изменение после подтверждения.
