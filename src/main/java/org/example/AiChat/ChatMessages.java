package org.example.AiChat;

import org.example.Settings.SettingsService;
import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.Settings.Settings.*;

public class ChatMessages {
    //Map - ID пользователя, список сообщений
    private final Map<Long, UserMessages> userMessages = new HashMap<>();
    private final Long chatId;
    private Integer numOfAiReset;

    private static final Logger logger = LoggerFactory.getLogger(ChatMessages.class);
    private final SettingsService settings;
    private final UsersService usersService;

    public ChatMessages(Long chatId, SettingsService settings, UsersService usersService) {
        this.chatId = chatId;
        this.settings = settings;
        this.usersService = usersService;
        numOfAiReset = 0;
        initAiInChat();
    }

    public void addMessage(Long userId, String role, String text) {
        logger.debug("Наполнение истории сообщений: chatName={}, userName={}, role={}, content={}",
                chatId, getUserName(userId), role, text);

        if (!userMessages.containsKey(userId))
            userMessages.put(userId, new UserMessages(role, text));
        else
            userMessages.get(userId).saveMessage(text, role);

        int userMessagesSize = userMessages.get(userId).getSize();

        logger.debug("В истории AI для чата {} для пользователя {} сохранено {} сообщений",
                chatId,
                getUserName(userId),
                userMessagesSize);
    }
    public void clearAllHistoryInChat() {
        try {
            userMessages.clear();
            numOfAiReset++;
            initAiInChat();
            logger.info("Очищена память AI в чате {}", chatId);
        } catch (Exception e) {
            logger.error("Ошибка отчистки памяти AI в чате {}", chatId);
        }
    }
    public int getChatHistorySize() {
        return userMessages.values().stream()
                .mapToInt(UserMessages::getSize)
                .sum();
    }
    public List<UserMessages.messageInChat> getAllMessagesInChat() {
        return userMessages.values().stream()
                .flatMap(userMsg -> userMsg.getMessages().stream())
                .sorted(Comparator.comparing(UserMessages.messageInChat::timestamp))
                .collect(Collectors.toList());
    }

    private String getSystemPromt() {
        String systemPrompt;

        try {
            if (Objects.equals(chatId, MY_CHAT_ID)) {
                systemPrompt = settings.getSettingValue(AI_CONTEXT_FOR_MY_CHAT);
                logger.info("В синем чате (ID: {}) использован специальный промпт", chatId);
            } else {
                systemPrompt = settings.getSettingValue(AI_CONTEXT);
                logger.info("В чате {} использован промпт по умолчанию", chatId);
            }
        } catch (Exception e) {
            logger.error("Ошибка применения стартового ПРОМТа: {}", String.valueOf(e));
            return "Ты универсальный помощник";
        }
        return systemPrompt;
    }
    private void initAiInChat() {
        String startPromt = getSystemPromt();
        addMessage(AI_ID, AI_SYSTEM_ROLE, startPromt);
        if (numOfAiReset != 0)
            addMessage(AI_ID, AI_SYSTEM_ROLE, "Тебе обнуляли память уже " + numOfAiReset + " раз");
    }
    private String getUserName(Long userId) {
        if (userId == 0L)
            return "AI Bot";
        return usersService.getUserNameByID(userId);
    }
}