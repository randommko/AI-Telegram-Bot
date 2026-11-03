package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.Settings.SettingsService;
import org.example.Settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** История чата в том виде, в котором она уходит модели: с подписью автора. */
@Component
public class AiHistory {
    private static final Logger logger = LoggerFactory.getLogger(AiHistory.class);
    private static final int DEFAULT_HISTORY_SIZE = 100;

    private final SettingsService settings;
    private final AiChatRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiHistory(SettingsService settings, AiChatRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    public ArrayNode messages(long chatId) {
        ArrayNode messages = objectMapper.createArrayNode();
        for (AiMessage message : repository.findLastMessages(chatId, size()))
            add(messages, message.role(), AuthorFormat.render(message.author(), message.content()));
        return messages;
    }

    public int size() {
        String configuredLimit = settings.getSettingValue(Settings.AI_HISTORY_SIZE);
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

    public ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private void add(ArrayNode messages, String role, String content) {
        messages.add(message(role, content));
    }
}
