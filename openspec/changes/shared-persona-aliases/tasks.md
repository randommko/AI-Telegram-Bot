# Tasks

## 1. Снять глобальную уникальность

- [x] 1.1 В `AdminPersonaValidator.checkAliases` убрать проверку `personas.aliasOwner(alias, personaId)`; остаются формат «одно слово» и запрет команд. Verify: в файле нет вызова `aliasOwner`.
- [x] 1.2 Удалить `PersonaRepository.aliasOwner` из `core`. Verify: `grep` по `aliasOwner` в `*.java` не находит совпадений.
- [x] 1.3 Обновить подсказку в `persona-form.html`: «Совпадать с алиасом другой персоны может: в чате всегда одна персона»; из `README.md` убрать «не занят другой персоной», добавить объяснение. Verify: текст совпадает с дельтой спеки.

## 2. Тесты и проверка

- [x] 2.1 В `AdminPersonaValidatorTest` заменить кейс отказа по занятости на приём: алиас, который есть у другой персоны, сохраняется; кейсы команд и формата остаются. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" -q -pl admin -am test -Dtest=AdminPersonaValidatorTest` — зелёный.
- [x] 2.2 Проверить изменение. Verify: `openspec validate shared-persona-aliases --type change --strict` — «is valid».
- [x] 2.3 Полная сборка. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" clean package` — `BUILD SUCCESS` (core 103, bot 153, admin 172).

## 3. Выкатка

- [ ] 3.1 Выкатить в `main` и дать пользователю проверить: у двух персон в разных чатах сохраняется одинаковый алиас, обе откликаются в своих чатах, дубль внутри одной персоны не появляется. Verify: пользователь подтверждает в интерфейсе и чате.

## Workflow follow-up

- Закрыть #82 с итогом выкатки.
- Архивировать изменение после подтверждения.
