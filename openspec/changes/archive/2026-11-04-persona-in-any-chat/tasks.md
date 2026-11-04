# Tasks

## 1. Триггеры ответа по персоне

- [x] 1.1 Заменить в `CommandDispatcher.answerNameAddress` ранний `return` по `MY_CHAT_ID` проверкой `personaService.personaForChat(chatId)`: пустой результат — выход, иначе сначала реплай, потом алиасы этого чата; проверить `mvn -q -Dtest=CommandDispatcherTest test` и что прежние сценарии «синего» чата остались зелёными
- [x] 1.2 Добавить в `CommandDispatcherTest` заглушку `personaForChat` во все сценарии обращения и реплая; проверить, что `addressInMyChatIsSentToAi`, `addressInAnotherChatIsNotSentToAi` и `replyToBotInAnotherChatIsNotSentToAi` отражают новое поведение
- [x] 1.3 Добавить тесты: другой чат с персоной отвечает на свой алиас; чат с другой персоной не реагирует на чужой алиас; чат без персоны молчит и на имя, и на реплай боту; реплай боту в другом чате с персоной уходит в AI; проверить `mvn -q -Dtest=CommandDispatcherTest test`

## 2. Имя персоны в сообщении «на связи»

- [x] 2.1 Собирать текст «`<имя персоны>` на связи, но вопроса нет. Напиши его после обращения.» из `persona.name()` вместо жёсткого «Юра»; проверить тестом в `CommandDispatcherTest`, что для персоны «Юра» текст не изменился, а для второй персоны называется её имя

## 3. Лог персоны

- [x] 3.1 Добавить имя персоны в строку `Системный промпт чата {}: разделов {}, символов {}` в `PromptComposer.compose` — `persona` с именем или «без персоны»; проверить тестом `PromptComposerTest` с logback `ListAppender` (образец — `LlmClientTest`), что для чата с персоной строка называет её имя, а для чата без персоны пишет «без персоны»

## 4. Уборка константы

- [x] 4.1 Удалить `MY_CHAT_ID` из `Settings` и заменить его в тестах локальной константой «синего» чата (`CommandDispatcherTest`, `PersonaServiceTest`); проверить `grep -r "MY_CHAT_ID" src` — совпадений нет, затем `mvn -q -Dtest='CommandDispatcherTest,PersonaServiceTest' test`

## 5. Интеграционная проверка

- [x] 5.1 Прогнать `mvn test` целиком и убедиться, что число тестов не уменьшилось, а правки не задели конвейер ответа (`AiServiceTest`, `PromptComposerTest`)
- [x] 5.2 Прогнать `openspec validate "persona-in-any-chat" --strict` и `openspec validate --specs --strict` — обе команды зелёные
- [x] 5.3 Проверить по спеке `ai-invocation`, что каждое изменённое требование совпадает по названию с основной спекой и что после архивации в ней не останется слова «выделенный чат»

## Workflow follow-up

- После зелёных проверок запушить в `main`, дождаться деплоя и проверить, что бот поднялся.
- Закрыть issue #62 комментарием с итогом (что сделано, где в коде, коммиты, номер run).
- Заархивировать изменение: `openspec archive persona-in-any-chat --yes --json`, затем снова `openspec validate --specs --strict` и `openspec list --json`.
