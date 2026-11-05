package org.example.bot;

import org.example.bot.aichat.AiAddressParser;
import org.example.bot.aichat.AiService;
import org.example.core.Command;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.Persona;
import org.example.core.aichat.PersonaService;
import org.example.core.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.example.core.settings.Settings.USER_ROLE;

@Component
public class CommandDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CommandDispatcher.class);

    private final MessageSender messageSender;
    private final AiService aiService;
    private final AiAddressParser addressParser;
    private final PersonaService personaService;
    private final KeyboardBuilder keyboardBuilder = new KeyboardBuilder();
    private final long botId;

    private final Map<Command, Consumer<Message>> handlers = Map.of(
            Command.BOT_INFO, this::handleBotInfo,
            Command.AI, this::handleAi,
            Command.AI_CLEAR_HISTORY, this::handleAiChatHistory
    );

    public CommandDispatcher(MessageSender messageSender,
                             AiService aiService,
                             AiAddressParser addressParser,
                             PersonaService personaService,
                             BotProperties properties) {
        this.messageSender = messageSender;
        this.aiService = aiService;
        this.addressParser = addressParser;
        this.personaService = personaService;
        this.botId = properties.botId();
    }

    public void dispatch(Update update) {
        Message message = update.getMessage();
        String text = message.getText();

        if (!text.startsWith("/")) {
            // Сохраняем оригинальное сообщение
            saveMessageText(message.getFrom(), message.getChat(), text);
            answerNameAddress(message, text);
            return;
        }

        String[] parts = text.split(" ", 2);
        String commandText = parts[0];

        if (parts.length > 1) {
            saveMessageText(message.getFrom(), message.getChat(), parts[1]);
        }

        Command command = Command.fromString(commandText);
        if (command == null) {
            logger.debug("Неизвестная команда: {}", commandText);
            return;
        }

        logger.debug("Выполняем команду: {}", command);

        if (handlers.containsKey(command)) {
            handlers.get(command).accept(message);
        }
    }

    private void answerNameAddress(Message message, String text) {
        Long chatId = message.getChatId();
        Optional<Persona> persona = personaService.personaForChat(chatId);
        if (persona.isEmpty())
            return;

        // Ответ реплаем на сообщение самого бота — второй триггер, имя в тексте не нужно.
        if (isReplyToBot(message)) {
            aiService.askAi(chatId, text);
            return;
        }

        List<String> aliases = personaService.aliasesForChat(chatId);
        addressParser.question(text, aliases).ifPresent(question -> {
            if (question.isEmpty()) {
                messageSender.sendMessage(chatId, persona.get().name()
                        + " на связи, но вопроса нет. Напиши его после обращения.");
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

    private void handleBotInfo(Message message) {
        String info = """
                Бот для общения с ИИ. Команды:
                /ai - Спросить AI
                /clear - Обнулить память AI (с подтверждением)
                """;
        messageSender.sendMessage(message.getChatId(), info);
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
