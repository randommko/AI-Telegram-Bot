package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_ASSISTANT_ROLE;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_SYSTEM_ROLE;

@Component
public class AiService {
    // Сколько последних сообщений чата помнит AI, если настройка не задана или испорчена.
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final float DEFAULT_ANSWER_TEMPERATURE = 1.0f;
    private static final float MAX_ANSWER_TEMPERATURE = 2.0f;

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final SettingsService settings;
    private final MessageSender sender;
    private final DeepSeekClient deepSeekClient;
    private final AiChatRepository repository;
    private final PromptComposer promptComposer;

    public AiService(SettingsService settings, MessageSender sender, DeepSeekClient deepSeekClient,
                     AiChatRepository repository, PromptComposer promptComposer) {
        this.settings = settings;
        this.sender = sender;
        this.deepSeekClient = deepSeekClient;
        this.repository = repository;
        this.promptComposer = promptComposer;
    }

    public void askAi(Message message) {
        String[] parts = message.getText().split(" ", 2);
        String userQuestion = parts.length > 1 ? parts[1] : "";
        askAi(message.getChatId(), userQuestion);
    }

    public void askAi(Long chatId, String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            sender.sendMessage(chatId, "Напиши свой вопрос после команды /ai");
            return;
        }

        ArrayNode history = getHistoryInChat(chatId);
        String aiAnswer = deepSeekClient.sendRequestToAi(history, answerTemperature());

        if (aiAnswer != null) {
            sender.sendLongMessage(chatId, aiAnswer);
            saveMessage(chatId, AI_ASSISTANT_ROLE, aiAnswer);
        }
    }

    public void saveMessage(Long chatId, String role, String messageToSave) {
        repository.saveMessage(chatId, role, messageToSave, getHistorySize());
    }

    public void saveMessage(Long chatId, String role, String messageToSave, MessageAuthor author) {
        repository.saveMessage(chatId, role, messageToSave, getHistorySize(), author);
    }

    public int getChatHistorySize(Long chatId) {
        return repository.countMessages(chatId);
    }

    public void clearChatHistory(Long chatId) {
        repository.clearMessages(chatId);
        int resets = repository.incrementResets(chatId);
        logger.info("Очищена память AI в чате {}, обнулений памяти: {}", chatId, resets);
    }

    private ArrayNode getHistoryInChat(Long chatId) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode messages = objectMapper.createArrayNode();

        // Промпт собирается на каждый запрос из разделов и не хранится сообщением,
        // поэтому правки в админке действуют сразу и не вытесняются лимитом истории.
        addMessage(messages, objectMapper, AI_SYSTEM_ROLE, promptComposer.compose(chatId).text());

        List<AiMessage> storedMessages = repository.findLastMessages(chatId, getHistorySize());
        for (AiMessage message : storedMessages)
            addMessage(messages, objectMapper, message.role(),
                    AuthorFormat.render(message.author(), message.content()));

        logger.info("Задаем вопрос AI. Подготовлена история сообщений в чате {}, всего сообщений в истории: {}",
                chatId, messages.size());
        return messages;
    }

    private void addMessage(ArrayNode messages, ObjectMapper objectMapper, String role, String content) {
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("role", role);
        messageNode.put("content", content);
        messages.add(messageNode);
    }

    /** Температура читается на каждый запрос: правка в админке действует без перезапуска. */
    private float answerTemperature() {
        float configured = settings.getFloat(AI_ANSWER_TEMPERATURE, DEFAULT_ANSWER_TEMPERATURE);
        if (configured < 0 || configured > MAX_ANSWER_TEMPERATURE) {
            logger.warn("Температура ответа {} вне диапазона 0..2: используется {}",
                    configured, DEFAULT_ANSWER_TEMPERATURE);
            return DEFAULT_ANSWER_TEMPERATURE;
        }
        return configured;
    }

    private int getHistorySize() {
        String configuredLimit = settings.getSettingValue(AI_HISTORY_SIZE);

        if (configuredLimit == null)
            return DEFAULT_HISTORY_SIZE;

        try {
            int limit = Integer.parseInt(configuredLimit.trim());
            if (limit > 0)
                return limit;
            logger.warn("Некорректный лимит истории AI '{}': используется {}", configuredLimit, DEFAULT_HISTORY_SIZE);
        } catch (NumberFormatException e) {
            logger.warn("Некорректный лимит истории AI '{}': используется {}", configuredLimit, DEFAULT_HISTORY_SIZE);
        }
        return DEFAULT_HISTORY_SIZE;
    }
}
