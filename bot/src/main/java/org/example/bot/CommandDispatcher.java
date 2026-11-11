package org.example.bot;

import org.example.bot.aichat.AiAddressParser;
import org.example.bot.aichat.AiService;
import org.example.core.Command;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.Persona;
import org.example.core.aichat.PersonaService;
import org.example.core.aichat.LlmCatalog;
import org.example.core.config.BotProperties;
import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

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
    private final SettingsService settingsService;
    private final KeyboardBuilder keyboardBuilder = new KeyboardBuilder();
    private final long botId;

    private final Map<Command, Consumer<Message>> handlers = Map.of(
            Command.BOT_INFO, this::handleBotInfo,
            Command.AI, this::handleAi,
            Command.AI_CLEAR_HISTORY, this::handleAiChatHistory,
            Command.MODEL_INFO, this::handleModelInfo
    );

    public CommandDispatcher(MessageSender messageSender,
                             AiService aiService,
                             AiAddressParser addressParser,
                             PersonaService personaService,
                             BotProperties properties,
                             SettingsService settingsService) {
        this.messageSender = messageSender;
        this.aiService = aiService;
        this.addressParser = addressParser;
        this.personaService = personaService;
        this.botId = properties.botId();
        this.settingsService = settingsService;
    }

    public void dispatch(Update update) {
        Message message = update.getMessage();
        String text = message.getText();

        if (!text.startsWith("/")) {
            // Сохраняем оригинальное сообщение
            saveMessageText(message.getFrom(), message.getChat(), text, message.getMessageId());
            answerNameAddress(message, text);
            return;
        }

        String[] parts = text.split(" ", 2);
        String commandText = parts[0];

        if (parts.length > 1) {
            // Текст команды реакций не получает: у него нет Telegram-id.
            saveMessageText(message.getFrom(), message.getChat(), parts[1], null);
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
            askAi(message, text);
            return;
        }

        List<String> aliases = personaService.aliasesForChat(chatId);
        addressParser.question(text, aliases).ifPresent(question -> {
            if (question.isEmpty()) {
                messageSender.sendMessage(chatId, persona.get().name()
                        + " на связи, но вопроса нет. Напиши его после обращения.");
                return;
            }
            askAi(message, question);
        });
    }

    /** Триггер передаётся явно: в гонке потоков «последнее сообщение в базе» может быть чужим. */
    private void askAi(Message message, String question) {
        aiService.askAi(message.getChatId(), question,
                AiService.replyToMessageId(message), MessageAuthor.from(message.getFrom()));
    }

    private boolean isReplyToBot(Message message) {
        Message replied = message.getReplyToMessage();
        return replied != null
                && replied.getFrom() != null
                && replied.getFrom().getId() != null
                && replied.getFrom().getId() == botId;
    }

    private void saveMessageText(User user, Chat chat, String text, Integer tgMessageId) {
        aiService.saveMessage(chat.getId(), USER_ROLE, text, MessageAuthor.from(user), tgMessageId);
    }

    private void handleBotInfo(Message message) {
        String info = """
                Бот для общения с ИИ. Команды:
                /ai - Спросить AI
                /clear - Обнулить память AI (с подтверждением)
                /model_info - Показать информацию о текущей модели ИИ
                """;
        messageSender.sendMessage(message.getChatId(), info);
    }

    private void handleModelInfo(Message message) {
        // Get the actual model being used by the bot
        String modelInfo = getModelInfo();
        messageSender.sendMessage(message.getChatId(), modelInfo);
    }

    private String getModelInfo() {
        // Get the actual model being used by resolving the current configuration
        LlmCatalog.Model actualModel = resolveActualModel();
        
        StringBuilder info = new StringBuilder();
        info.append("Информация о текущей модели:\n\n");
        
        // Provider information
        info.append("Провайдер: ").append(actualModel.providerId()).append("\n");
        
        // Model identifier
        info.append("Идентификатор модели: ").append(actualModel.id()).append("\n");
        
        // Display name
        info.append("Отображаемое название: ").append(actualModel.title()).append("\n");
        
        // Connection method (direct or aggregator)
        LlmCatalog.Provider provider = LlmCatalog.provider(actualModel.providerId())
                .orElse(null);
        if (provider != null) {
            info.append("Способ подключения: ")
                .append(provider.aggregator() ? "через агрегатор" : "прямое подключение")
                .append("\n");
        }
        
        // Output token limit
        info.append("Потолок выходных токенов модели: ").append(actualModel.maxOutputTokens()).append("\n");
        
        // Current answer limit (from settings)
        int maxTokens = settingsService.getInt("aiMaxTokensAnswerQuestion", 4096);
        info.append("Действующий лимит ответа: ").append(maxTokens).append("\n");
        
        // Reaction model setting
        boolean reactionModelUseOwn = settingsService.getBoolean("aiReactionModelUseOwn", false);
        info.append("Модель для реакций: ")
            .append(reactionModelUseOwn ? "своя модель" : "основная модель")
            .append("\n");
            
        // Cost information (if available)
        if (actualModel.inputPricePerMillion() != null && actualModel.outputPricePerMillion() != null) {
            info.append("Стоимость модели за миллион токенов:\n");
            info.append("  Входных токенов: ").append(actualModel.inputPricePerMillion()).append(" руб.\n");
            info.append("  Выходных токенов: ").append(actualModel.outputPricePerMillion()).append(" руб.\n");
        } else {
            info.append("Стоимость модели: информация отсутствует\n");
        }
        
        return info.toString();
    }
    
    private LlmCatalog.Model resolveActualModel() {
        // This mimics the logic from LlmClient.resolveModel() to get the actual model
        String configuredModel = settingsService.getModel();
        String configuredRoute = settingsService.getRoute();
        
        // If we have a configured route, use it to resolve the model
        if (configuredRoute != null && !configuredRoute.isEmpty()) {
            Optional<LlmCatalog.Model> model = LlmCatalog.model(configuredRoute, configuredModel);
            if (model.isPresent()) {
                return model.get();
            }
        }
        
        // Fall back to resolving without route (direct provider)
        Optional<LlmCatalog.Model> model = LlmCatalog.model(configuredModel);
        if (model.isPresent()) {
            return model.get();
        }
        
        // Fallback to default model
        return LlmCatalog.model("deepseek-flash")
                .orElseThrow(() -> new IllegalStateException("В каталоге нет модели по умолчанию"));
    }

    private void handleAi(Message message) {
        Long chatId = message.getChatId();
        Optional<Persona> persona = personaService.personaForChat(chatId);
        if (persona.isPresent()) {
            String name = persona.get().name();
            messageSender.sendMessage(chatId, "В этом чате отвечает " + name
                    + ". Напиши вопрос с обращением — например: «" + name + ", как дела?»");
            logger.info("Команда {} в чате {} отклонена: назначена персона {}",
                    message.getText().split(" ", 2)[0], chatId, name);
            return;
        }
        aiService.askAi(message);
    }

    private void handleAiChatHistory(Message message) {
        Long chatId = message.getChatId();
        int forgottenCount = aiService.getChatHistorySize(chatId);

        keyboardBuilder.sendAiClearQuestion(messageSender, chatId, message.getFrom().getId(), forgottenCount);

        logger.info("Запрошен сброс истории AI в чате {}: будет удалено воспоминаний: {}", chatId, forgottenCount);
    }
}
