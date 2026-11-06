# Tasks

## 1. Команда и диспетчер

- [x] 1.1 Сократить `Command.AI` до `/ai`, `/ии`, `/аи` в `core/src/main/java/org/example/core/Command.java`. Verify: `grep` по `core/src/main` не находит `/юра`, `/юрий`, `/юрец`; `Command.fromString("/юра как дела")` возвращает `null`.
- [x] 1.2 В `CommandDispatcher.handleAi` добавить проверку `personaService.personaForChat(chatId)`: персона есть → отправить подсказку `В этом чате отвечает <имя>. Напиши вопрос с обращением — например: «<имя>, как дела?»`, записать `logger.info` об отклонении и выйти; персоны нет → `aiService.askAi(message)` как сейчас. Verify: следующий пункт и чтение кода — подсказка собирается из `persona.name()`, а не из константы.
- [x] 1.3 Обновить `bot/src/test/java/org/example/bot/CommandDispatcherTest.java`: `slashCommandStillWorksAsBefore` перевести с `/юра` на `/ai` в чате без персоны; добавить кейсы «`/ai` в чате с персоной → подсказка, `askAi` не вызван», «`/ai` без вопроса в чате с персоной → та же подсказка», «`/ai` в чате без персоны → `askAi`», «`/юра вопрос` в чате с персоной → `askAi` не вызван, текст сохранён», «`юра, как дела?` без слэша → `askAi`». Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" -q -pl bot -am test -Dtest=CommandDispatcherTest` — все тесты зелёные.

## 2. Валидатор админки

- [x] 2.1 Удалить `PERSONA_ADDRESS_NAMES` и исключение в `forbiddenCommands()` в `admin/src/main/java/org/example/admin/AdminPersonaValidator.java`; список запрещённых имён строится из `Command.aliases()` без особых случаев. Verify: в файле не осталось упоминаний `PERSONA_ADDRESS_NAMES`.
- [x] 2.2 Обновить кейс с алиасом `юра` в `admin/src/test/java/org/example/admin/AdminPersonaValidatorTest.java`: алиас принимается как обычный, тест не ссылается на удалённое исключение. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" -q -pl admin -am test -Dtest=AdminPersonaValidatorTest` — зелёный.

## 3. Спеки и документация

- [x] 3.1 Поправить `Purpose` в `openspec/specs/ai-invocation/spec.md`: вместо «слэш-команды в любом чате» — слэш-команда только в чате без персоны, обращение по алиасу и реплай — в чате с персоной. Verify: в `Purpose` нет слов «в любом чате»; само требование с шестью командами остаётся до архивации, его заменяет дельта.
- [x] 3.2 Обновить `README.md`: строку про `/ai`, `/ии`, `/аи` («работают в любом чате как раньше») заменить правилом про персону и подсказку, из проверок карточки персоны убрать список разрешённых имён-обращений. Verify: `grep -n "в любом чате" README.md` не находит совпадений, описание совпадает с дельтами.
- [x] 3.3 Проверить изменение целиком. Verify: `openspec validate persona-only-ai-in-chat --type change --strict` — «is valid».

## 4. Сборка и выкатка

- [ ] 4.1 Собрать оба jar. Verify: `& "C:\Program Files\apache-maven-3.8.7\bin\mvn.cmd" clean package` завершается успешно, `bot/target/telegram-bot-1.0.jar` и `admin/target/telegram-bot-admin-1.0.jar` обновлены.
- [ ] 4.2 Выкатить в `main` (push перезапускает бота) и дать пользователю прогнать в Telegram: `/ai вопрос` в чате без персоны — ответ; `/ai вопрос` в «синем» чате — подсказка с именем персоны; `/юра вопрос` — тишина; «юра, как дела?» — ответ. Verify: пользователь подтверждает поведение в чате, в логе есть строка об отклонённой команде.

## Workflow follow-up

- Архивировать изменение после подтверждения работы на сервере.
- Если под изменение заведена задача GitHub — закрыть её с итогом выкатки.
