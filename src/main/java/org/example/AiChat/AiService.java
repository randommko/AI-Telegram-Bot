package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.Settings.Settings.*;

@Component
public class AiService {
    private final Map<Long, ChatMessages> chatHistory = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(AiService.class);
    private final SettingsService settings;
    private final UsersService usersService;
    private final MessageSender sender;
    private final DeepSeekClient deepSeekClient;

    private final Float answerTemperature;

    public AiService(SettingsService settings, UsersService usersService, MessageSender sender,
                     DeepSeekClient deepSeekClient) {
        this.settings = settings;
        this.usersService = usersService;
        this.sender = sender;
        this.deepSeekClient = deepSeekClient;
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
            saveMessage(chatId, AI_ID, AI_ASSISTANT_ROLE, aiAnswer);
        }
    }
    public void saveMessage(Long chatId, Long userId, String role, String messageToSave) {
        if (!chatHistory.containsKey(chatId))
            chatHistory.put(chatId, new ChatMessages(chatId, settings, usersService));

        chatHistory.get(chatId).addMessage(userId, role, messageToSave);
    }
    public int getChatHistorySize(Long chatId) {
        return chatHistory.get(chatId).getChatHistorySize();
    }
    public void clearChatHistory(Long chatId) {
        chatHistory.get(chatId).clearAllHistoryInChat();
    }

    private ArrayNode getHistoryInChat(Long chatId) {
        ChatMessages chatMessages = chatHistory.get(chatId);
        ObjectMapper objectMapper = new ObjectMapper();
        if (chatMessages == null) {
            logger.error("Чат с ID {} не найден", chatId);
            return objectMapper.createArrayNode();
        }

        List<UserMessages.messageInChat> messagesList = chatMessages.getAllMessagesInChat();

        ArrayNode messages = objectMapper.createArrayNode();

        // Преобразуем в JSON формат для DeepSeek API
        for (UserMessages.messageInChat msg : messagesList) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", msg.role());  // Используем role из record
            messageNode.put("content", msg.content());
            messages.add(messageNode);
        }

        logger.info("Задаем вопрос AI. Подготовлена история сообщений в чате {}, всего сообщений в истории: {}",
                chatId, messages.size());
        return messages;
    }


}
