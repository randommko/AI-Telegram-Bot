package org.example.aichat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Разбирает ответ генерации: массив строк или объект с полем replies. */
@Component
public class ReplyParser {
    private static final Logger logger = LoggerFactory.getLogger(ReplyParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> parse(String raw, int maxReplies) {
        if (raw == null || raw.isBlank())
            return List.of();

        JsonNode root;
        try {
            root = objectMapper.readTree(AnswerPlanParser.jsonBody(raw));
        } catch (Exception e) {
            logger.warn("Реплики не разобраны: {}", String.valueOf(e));
            return List.of();
        }

        JsonNode array = root.isArray() ? root : root.path("replies");
        if (!array.isArray()) {
            logger.warn("Ответ генерации не содержит массива реплик");
            return List.of();
        }

        List<String> replies = new ArrayList<>();
        int dropped = 0;
        for (JsonNode element : array) {
            if (!element.isTextual()) {
                dropped++;
                continue;
            }
            String reply = element.asText("").strip();
            if (reply.isEmpty()) {
                dropped++;
                continue;
            }
            if (replies.size() >= maxReplies) {
                dropped++;
                continue;
            }
            replies.add(reply);
        }

        if (dropped > 0)
            logger.info("Реплик отброшено: {} (пустые или сверх лимита {})", dropped, maxReplies);
        return replies;
    }
}
