package org.example;


import org.example.AiChat.AiAddressParser;
import org.example.AiChat.AiService;
import org.example.AiChat.MessageAuthor;
import org.example.AiChat.PersonaService;
import org.example.CockSize.CockSizeGame;
import org.example.Config.BotProperties;

import org.example.PidorGame.PidorGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.example.Settings.Settings.MY_CHAT_ID;
import static org.example.Settings.Settings.USER_ROLE;

@Component
public class CommandDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CommandDispatcher.class);

    private final MessageSender messageSender;
    private final CockSizeGame cockSizeGame;
    private final PidorGame pidorGame;
    private final AiService aiService;
    private final AiAddressParser addressParser;
    private final PersonaService personaService;
    private final KeyboardBuilder keyboardBuilder = new KeyboardBuilder();
    private final long botId;

    // Enum для команд (расширяемо)
    public enum Command {
        BOT_INFO("/bot_info", "/help"),
        COCKSIZE("/cocksize"),
        PIDOR_REG("/pidor_reg"),
        PIDOR_STATS("/pidor_stats"),
        PIDOR_START("/pidor_start"),
        HOROSCOPE("/horoscope_today"),
        AI("/ai", "/ии", "/аи", "/юра", "/юрий", "/юрец"),
        SUMMARY("/summary"),
        AI_CLEAR_HISTORY("/clear");

        private final List<String> aliases;

        Command(String... aliases) {
            this.aliases = List.of(aliases);
        }

        /** Все написания команды — нужны проверке алиасов персоны в админке. */
        public List<String> aliases() {
            return aliases;
        }

        public static Command fromString(String cmd) {
            String cleanCmd = cmd.toLowerCase().trim();
            for (Command command : values()) {
                if (command.aliases.stream().anyMatch(alias ->
                        cleanCmd.startsWith(alias.toLowerCase()) ||
                                cleanCmd.equals(alias.replace("/", "").toLowerCase()))) {
                    return command;
                }
            }
            return null;
        }
    }

    // EnumMap для диспетчеризации
    private final Map<Command, Consumer<Message>> handlers = Map.of(
            Command.BOT_INFO, this::handleBotInfo,
            Command.COCKSIZE, this::handleCockSize,
            Command.PIDOR_REG, this::handlePidorReg,
            Command.PIDOR_STATS, this::handlePidorStats,
            Command.PIDOR_START, this::handlePidorStart,
            Command.HOROSCOPE, this::handleHoroscope,
            Command.AI, this::handleAi,
            Command.AI_CLEAR_HISTORY, this::handleAiChatHistory
    );

    public CommandDispatcher(MessageSender messageSender,
                             CockSizeGame cockSizeGame,
                             PidorGame pidorGame,
                             AiService aiService,
                             AiAddressParser addressParser,
                             PersonaService personaService,
                             BotProperties properties) {
        this.messageSender = messageSender;
        this.cockSizeGame = cockSizeGame;
        this.pidorGame = pidorGame;
        this.aiService = aiService;
        this.addressParser = addressParser;
        this.personaService = personaService;
        this.botId = properties.botId();
    }

    public void dispatch(Update update) {
        Message message = update.getMessage();
        String text = message.getText();

        if (!text.startsWith("/")) {
            //Сохраняем оригинальное сообщение
            saveMessageText(message.getFrom(), message.getChat(), text);
            answerNameAddress(message, text);
            return;
        }

        String[] parts = text.split(" ", 2);

        String commandText = parts[0]; // Команда всегда в первой части

        // Проверяем, есть ли аргументы после команды
        if (parts.length > 1) {
            // Сохраняем текст после команды (аргументы)
            saveMessageText(message.getFrom(), message.getChat(), parts[1]);
        }

        Command command = Command.fromString(parts[0]);
        if (command == null) {
            logger.debug("Неизвестная команда: {}", parts[0]);
            return;
        }

        logger.debug("Выполняем команду: {}", command);

        if (handlers.containsKey(command)) {
            handlers.get(command).accept(message);
        }
    }


    private void answerNameAddress(Message message, String text) {
        Long chatId = message.getChatId();
        if (!MY_CHAT_ID.equals(chatId))
            return;

        // Ответ реплаем на сообщение самого бота — второй триггер, имя в тексте не нужно.
        if (isReplyToBot(message)) {
            aiService.askAi(chatId, text);
            return;
        }

        List<String> aliases = personaService.aliasesForChat(chatId);
        addressParser.question(text, aliases).ifPresent(question -> {
            if (question.isEmpty()) {
                messageSender.sendMessage(chatId, "Юра на связи, но вопроса нет. Напиши его после обращения.");
                return;
            }
            aiService.askAi(chatId, question);
        });
    }

    private boolean isReplyToBot(Message message) {
        Message replied = message.getReplyToMessage();
        return replied != null
                && replied.getFrom() != null
                && replied.getFrom().getId() != null
                && replied.getFrom().getId() == botId;
    }

    private void saveMessageText(User user, Chat chat, String text) {
        aiService.saveMessage(chat.getId(), USER_ROLE, text, MessageAuthor.from(user));
    }

    // Хендлеры команд (каждый - 1 ответственность)
    private void handleBotInfo(Message message) {
        String info = """
            Бот для развлечений! Команды:
            /cocksize - Измерить достоинство
            /pidor_start - Выбрать пидора дня
            /horoscope_today - Гороскоп
            /pidor_reg - Вступить в ряды пидоров
            /pidor_stats - Статистика пидоров
            /ai - Спроси GigaChat
            /clear - Обнулить память AI (с подтверждением)
            /summary - Пересказ последних 100 сообщений
            """;
        messageSender.sendMessage(message.getChatId(), info);
    }
    private void handleCockSize(Message message) {
        cockSizeGame.sendTodayCockSize(message);
    }
    private void handlePidorReg(Message message) {
        pidorGame.registerPlayer(message.getChatId(), message.getFrom().getId());
    }
    private void handlePidorStats(Message message) {
        pidorGame.sendPidorStats(message.getChatId());
    }
    private void handlePidorStart(Message message) {
        pidorGame.startPidorGame(message.getChatId(), message.getFrom().getId());
    }
    private void handleHoroscope(Message message) {
        keyboardBuilder.sendHoroscopeKeyboard(messageSender, message.getChatId());
    }
    private void handleAi(Message message) {
        aiService.askAi(message);
    }
    private void handleAiChatHistory(Message message) {
        Long chatId = message.getChatId();
        int forgottenCount = aiService.getChatHistorySize(chatId);

        keyboardBuilder.sendAiClearQuestion(messageSender, chatId, message.getFrom().getId(), forgottenCount);

        logger.info("Запрошен сброс истории AI в чате {}: будет удалено воспоминаний: {}", chatId, forgottenCount);
    }

}
