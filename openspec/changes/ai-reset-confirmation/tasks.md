# Tasks

## 1. Клавиатура подтверждения и разбор `callback_data`

- [x] 1.1 В `KeyboardBuilder` добавить, по образцу `sendHoroscopeKeyboard`, отправку вопроса «Обнулить память AI в этом чате? Забыто будет N сообщений» с кнопками «Да» и «Отмена» и `callback_data` вида `aiClear:yes:<userId>:<N>` / `aiClear:no:<userId>:<N>`. Проверка: тест на моке бота захватывает `SendMessage`, убеждается, что клавиатура содержит ровно эти две кнопки, и проверяет длину `callback_data` в байтах для 19-значного id (не больше 64).
- [x] 1.2 Добавить разбор `callback_data` (действие + id автора + N) рядом с `Zodiac.fromCallback`. Проверка: юнит-тест покрывает `yes`, `no`, id максимальной длины и постороннюю строку — последняя даёт пустой результат, чтобы сработала существующая ветка «Неизвестная кнопка».

## 2. Уведомление из хендлера callback

- [x] 2.1 Изменить `CallbackDispatcher.CallbackHandler` на возврат строки-уведомления (`null` — без уведомления) и передавать её в `answerCallbackQuery(callback, text)` внутри `dispatch`; существующие хендлеры возвращают `null`. Проверка: новый `CallbackDispatcherTest` — нажатие средней кнопки размера вызывает `AnswerCallbackQuery` без текста ровно один раз.
- [x] 2.2 Тест: хендлер, вернувший текст, приводит к `AnswerCallbackQuery` с этим текстом и тоже ровно к одному ответу (иначе Telegram вернёт «query is too old»).

## 3. Команда `/clear` присылает вопрос вместо сброса

- [x] 3.1 `CommandDispatcher.handleAiChatHistory`: посчитать N через `getChatHistorySize`, отправить вопрос с клавиатурой, убрать вызов `clearChatHistory` и оба `Thread.sleep(1000)`. Проверка: `CommandDispatcherTest` — после `/clear` `clearChatHistory` не вызывается, в отправленном сообщении есть число N.
- [x] 3.2 `CommandDispatcherTest`: текст `/bot_info` содержит строку про `/clear`.

## 4. Обработчик подтверждения

- [x] 4.1 В `CallbackDispatcher` добавить обработку подтверждения: сверка `callback.getFrom().getId()` с автором из `callback_data`; «Да» → `clearChatHistory` и правка сообщения в «Память обнулена, забыто N», «Отмена» → правка в «Отменено», чужое нажатие → уведомление без правки сообщения и без изменений истории. Класс получает зависимость `AiService`. Проверка: тест на четыре случая (свой «Да», свой «Отмена», чужой «Да», чужой «Отмена») — `clearChatHistory` вызывается только в первом, `editMessage` не вызывается при чужом нажатии.
- [x] 4.2 `MessageSender.editMessage` убирает inline-клавиатуру (пустая `InlineKeyboardMarkup`), чтобы после ответа кнопки нельзя было нажать снова. Проверка: тест захватывает `EditMessageText` и убеждается, что в нём пустая клавиатура.

## 5. Документация команды

- [x] 5.1 Добавить `/clear` в список команд `README.md`. Проверка: строка присутствует в файле и совпадает по смыслу с текстом `/bot_info` из задачи 3.2.

## 6. Интеграционная проверка

- [x] 6.1 Прогнать весь набор: `set "JAVA_HOME=C:\Program Files\Java\jdk-17" && mvn -q test` — зелёный, включая `CommandDispatcherTest`, `CallbackDispatcherTest`, `AiServiceTest`.
- [x] 6.2 Собрать jar и прогнать бут-чек перед выкатом: временный PostgreSQL на 55432 с миграциями `001`–`003`, фиктивные `BOT_*` и мёртвый прокси; ожидаемо `HikariPool - Start completed`, падение только на `api.telegram.org`.

## Workflow follow-up

- Выкатить в `main` (push деплоит и перезапускает бота) и дать пользователю прогнать в чате: `/clear` → «Отмена» (история на месте), затем `/clear` → «Да» (память обнулена, `/ai` не помнит контекст).
- Закрыть GitHub issue #40 с итогом по-русски.
- После подтверждённого смоука: `openspec archive ai-reset-confirmation --yes`, затем `openspec validate --specs --strict` и `openspec list --json`.
