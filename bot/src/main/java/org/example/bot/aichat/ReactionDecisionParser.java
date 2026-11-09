package org.example.bot.aichat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Разбирает ответ модели на проход чтения. Строгий JSON: нет списка реакций —
 * проход считается неудачным. Отдельные некорректные элементы отбрасываются,
 * чтобы одна ошибка модели не отменяла остальные реакции пачки.
 */
@Component
public class ReactionDecisionParser {
    private static final Logger logger = LoggerFactory.getLogger(ReactionDecisionParser.class);
    static final int MAX_REACTIONS = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Реакция на конкретное сообщение пачки. */
    public record ReactionDecision(int messageId, String emoji, String reason) {
    }

    /**
     * @param messageIds идентификаторы сообщений пачки — реакция на другое сообщение не ставится
     * @param emojis     включённые эмоциональные эмодзи каталога
     */
    public List<ReactionDecision> parse(String raw, Set<Integer> messageIds, Set<String> emojis) {
        String json = stripFences(raw);
        if (json.isEmpty()) {
            logger.warn("Проход чтения: модель вернула пустой ответ");
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            logger.warn("Проход чтения: ответ модели не является JSON: {}", e.getMessage());
            return List.of();
        }

        JsonNode array = root == null ? null : root.get("reactions");
        if (array == null || !array.isArray()) {
            logger.warn("Проход чтения: в ответе модели нет массива reactions");
            return List.of();
        }

        List<ReactionDecision> reactions = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (JsonNode node : array) {
            if (reactions.size() >= MAX_REACTIONS)
                break;
            if (!node.isObject()) {
                logger.warn("Проход чтения: элемент reactions не объект");
                continue;
            }

            Integer messageId = intValue(node.get("message_id"));
            if (messageId == null || !messageIds.contains(messageId)) {
                logger.warn("Проход чтения: реакция на сообщение вне пачки отброшена: {}", node.get("message_id"));
                continue;
            }
            if (!used.add(messageId))
                continue;

            String emoji = text(node, "emoji");
            if (emoji.isEmpty() || !emojis.contains(emoji)) {
                logger.warn("Проход чтения: эмодзи «{}» вне активного каталога — отброшено", emoji);
                continue;
            }

            reactions.add(new ReactionDecision(messageId, emoji, text(node, "reason")));
        }
        return reactions;
    }

    private Integer intValue(JsonNode node) {
        if (node == null || node.isNull())
            return null;
        if (node.canConvertToInt())
            return node.asInt();
        try {
            return Integer.parseInt(node.asText("").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String stripFences(String raw) {
        if (raw == null)
            return "";
        String text = raw.trim();
        if (!text.startsWith("```"))
            return text;

        int newline = text.indexOf('\n');
        if (newline < 0)
            return text;
        text = text.substring(newline + 1).trim();
        if (text.endsWith("```"))
            text = text.substring(0, text.length() - 3);
        return text.trim();
    }
}
