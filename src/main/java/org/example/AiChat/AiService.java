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

import static org.example.Settings.Settings.*;

@Component
public class AiService {
    // Сколько последних сообщений чата помнит AI, если настройка не задана или испорчена.
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final int DEFAULT_MEMORY_PROMPT_SIZE = 30;
    private static final float DEFAULT_ANSWER_TEMPERATURE = 1.0f;
    private static final float MAX_ANSWER_TEMPERATURE = 2.0f;
    private static final String DEFAULT_SYSTEM_PROMPT = "Ты универсальный помощник";

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final SettingsService settings;
    private final MessageSender sender;
    private final DeepSeekClient deepSeekClient;
    private final AiChatRepository repository;
    private final PersonaService personaService;
    private final MemoryRepository memoryRepository;
    private final MemoryPromptBuilder memoryPromptBuilder;
    private final EmotionService emotionService;

    public AiService(SettingsService settings, MessageSender sender, DeepSeekClient deepSeekClient,
                     AiChatRepository repository, PersonaService personaService,
                     MemoryRepository memoryRepository, MemoryPromptBuilder memoryPromptBuilder,
                     EmotionService emotionService) {
        this.settings = settings;
        this.sender = sender;
        this.deepSeekClient = deepSeekClient;
        this.repository = repository;
        this.personaService = personaService;
        this.memoryRepository = memoryRepository;
        this.memoryPromptBuilder = memoryPromptBuilder;
        this.emotionService = emotionService;
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
        addMood(messages, objectMapper, chatId);
        addMemories(messages, objectMapper, chatId);

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

    /** Эмоциональное состояние персоны: блок фраз, только если оно отошло от характера. */
    private void addMood(ArrayNode messages, ObjectMapper objectMapper, Long chatId) {
        try {
            var persona = personaService.personaForChat(chatId);
            if (persona.isEmpty())
                return;

            String block = emotionService.blockFor(persona.get().id());
            if (block == null || block.isBlank())
                return;

            addMessage(messages, objectMapper, AI_SYSTEM_ROLE, block);
            logger.info("В чате {} добавлен блок настроения, символов {}", chatId, block.length());
        } catch (Exception e) {
            logger.error("Ошибка сборки настроения для чата {}: {}", chatId, String.valueOf(e));
        }
    }

    private void addMemories(ArrayNode messages, ObjectMapper objectMapper, Long chatId) {
        try {
            var persona = personaService.personaForChat(chatId);
            if (persona.isEmpty())
                return;

            List<MemoryRecord> core = memoryRepository.findCore(persona.get().id());
            List<MemoryRecord> regular = memoryRepository.findTopRegular(persona.get().id(), getMemoryPromptSize());
            String block = memoryPromptBuilder.build(core, regular);
            if (block.isBlank())
                return;

            addMessage(messages, objectMapper, AI_SYSTEM_ROLE, block);
            logger.info("В чате {} в промпт добавлено памяти: ядровых {}, обычных {}, символов {}",
                    chatId, core.size(), regular.size(), block.length());
        } catch (Exception e) {
            logger.error("Ошибка сборки памяти для чата {}: {}", chatId, String.valueOf(e));
        }
    }

    private void addMessage(ArrayNode messages, ObjectMapper objectMapper, String role, String content) {
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("role", role);
        messageNode.put("content", content);
        messages.add(messageNode);
    }

    private String getSystemPrompt(Long chatId) {
        try {
            var resolved = personaService.resolvePrompt(chatId);
            if (resolved.isPresent() && !resolved.get().text().isBlank()) {
                logger.info("В чате (ID: {}) использован промпт {}", chatId, resolved.get().source());
                return resolved.get().text();
            }
        } catch (Exception e) {
            logger.error("Ошибка выбора промпта для чата {}: {}", chatId, String.valueOf(e));
        }
        logger.info("В чате {} использован промпт по умолчанию", chatId);
        return DEFAULT_SYSTEM_PROMPT;
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

    private int getMemoryPromptSize() {
        String configuredLimit = settings.getSettingValue(AI_MEMORY_PROMPT_SIZE);

        if (configuredLimit == null)
            return DEFAULT_MEMORY_PROMPT_SIZE;

        try {
            int limit = Integer.parseInt(configuredLimit.trim());
            // Ноль — допустимое значение: в промпт идёт только ядро.
            if (limit >= 0)
                return limit;
            logger.warn("Некорректный размер памяти в промпте '{}': используется {}",
                    configuredLimit, DEFAULT_MEMORY_PROMPT_SIZE);
        } catch (NumberFormatException e) {
            logger.warn("Некорректный размер памяти в промпте '{}': используется {}",
                    configuredLimit, DEFAULT_MEMORY_PROMPT_SIZE);
        }
        return DEFAULT_MEMORY_PROMPT_SIZE;
    }
}
