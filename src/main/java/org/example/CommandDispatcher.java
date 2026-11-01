package org.example;


import org.example.AiChat.AiService;
import org.example.CockSize.CockSizeGame;

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

import static org.example.Settings.Settings.USER_ROLE;

@Component
public class CommandDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CommandDispatcher.class);

    private final MessageSender messageSender;
    private final CockSizeGame cockSizeGame;
    private final PidorGame pidorGame;
    private final AiService aiService;
    private final KeyboardBuilder keyboardBuilder = new KeyboardBuilder();

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
                             AiService aiService) {
        this.messageSender = messageSender;
        this.cockSizeGame = cockSizeGame;
        this.pidorGame = pidorGame;
        this.aiService = aiService;
    }

    public void dispatch(Update update) {
        Message message = update.getMessage();
        String text = message.getText();

        if (!text.startsWith("/")) {
            //Сохраняем оригинальное сообщение
            saveMessageText(message.getFrom(), message.getChat(), text);
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


    private void saveMessageText(User user, Chat chat, String text) {
        String userName = getUserName(user);
        Long userId = user.getId();
        Long chatId = chat.getId();

        String messageToSave = "Сообщение от: @" + userName + ": " + text;

        aiService.saveMessage(chatId,
                userId,
                USER_ROLE,
                messageToSave);

    }
    private String getUserName(User user) {
        String userName;
        if (user.getUserName() == null)
            userName = user.getFirstName();
        else
            userName = user.getUserName();
        return userName;
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
        try {
            String clearMsg = "Запуск принудительной отчистки истории AI, будет удалено воспоминаний: " + aiService.getChatHistorySize(chatId);
            messageSender.sendMessage(chatId, clearMsg);
            Thread.sleep(1000);

            clearMsg = "Было приятно помнить вас \uD83D\uDE22 \uD83D\uDE22 \uD83D\uDE22";
            messageSender.sendMessage(chatId, clearMsg);

            Thread.sleep(1000);
            aiService.clearChatHistory(chatId);

            String successClearMsg = "Принудительная отчистка истории AI в чате выполнена. Воспоминаний в памяти: 0.";
            messageSender.sendMessage(chatId, successClearMsg);

            logger.info("Выполнена принудительная отчистка истории AI в чате: {}", message.getChat().getTitle());
        }
        catch (Exception e) {
            logger.error("Ошибка отчистки памяти AI: {}", e.toString());
        }

    }

}
