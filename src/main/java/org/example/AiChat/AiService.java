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
import java.util.Objects;

import static org.example.Settings.Settings.*;

@Component
public class AiService {
    // Сколько последних сообщений чата помнит AI, если настройка не задана или испорчена.
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final String DEFAULT_SYSTEM_PROMPT = "Ты универсальный помощник";

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final SettingsService settings;
    private final MessageSender sender;
    private final DeepSeekClient deepSeekClient;
    private final AiChatRepository repository;

    private final Float answerTemperature;

    public AiService(SettingsService settings, MessageSender sender, DeepSeekClient deepSeekClient,
                     AiChatRepository repository) {
        this.settings = settings;
        this.sender = sender;
        this.deepSeekClient = deepSeekClient;
        this.repository = repository;
        this.answerTemperature = Float.valueOf(settings.getSettingValue(AI_ANSWER_TEMPERATURE));
    }

    public void askAi(Message message) {
        String[] parts = message.getText().split(" ", 2);
        String userQuestion = parts.length > 1 ? parts[1] : "";
        Long chatId = message.getChatId();

        if (userQuestion.isBlank()) {
            sender.sendMessage(chatId, "Напиши свой вопрос после команды /ai");
            return;
        }

        ArrayNode history = getHistoryInChat(chatId);
        String aiAnswer = deepSeekClient.sendRequestToAi(history, answerTemperature);

        if (aiAnswer != null) {
            sender.sendMessage(chatId, aiAnswer);
            saveMessage(chatId, AI_ASSISTANT_ROLE, aiAnswer);
        }
    }

    public void saveMessage(Long chatId, String role, String messageToSave) {
        repository.saveMessage(chatId, role, messageToSave, getHistorySize());
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

        // Промпт и счётчик сбросов не хранятся сообщениями: так промпт всегда актуален
        // и не вытесняется лимитом истории.
        addMessage(messages, objectMapper, AI_SYSTEM_ROLE, getSystemPrompt(chatId));

        int resets = repository.getResets(chatId);
        if (resets > 0)
            addMessage(messages, objectMapper, AI_SYSTEM_ROLE, "Тебе обнуляли память уже " + resets + " раз");

        List<AiMessage> storedMessages = repository.findLastMessages(chatId, getHistorySize());
        for (AiMessage message : storedMessages)
            addMessage(messages, objectMapper, message.role(), message.content());

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

    private String getSystemPrompt(Long chatId) {
        try {
            boolean myChat = Objects.equals(chatId, MY_CHAT_ID);
            String systemPrompt = myChat
                    ? settings.getSettingValue(AI_CONTEXT_FOR_MY_CHAT)
                    : settings.getSettingValue(AI_CONTEXT);

            logger.info(myChat
                            ? "В синем чате (ID: {}) использован специальный промпт"
                            : "В чате {} использован промпт по умолчанию",
                    chatId);

            if (systemPrompt != null && !systemPrompt.isBlank())
                return systemPrompt;
        } catch (Exception e) {
            logger.error("Ошибка применения стартового ПРОМТа: {}", String.valueOf(e));
        }
        return DEFAULT_SYSTEM_PROMPT;
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
