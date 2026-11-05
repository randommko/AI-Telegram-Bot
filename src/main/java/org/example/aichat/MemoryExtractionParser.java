package org.example.aichat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Разбирает ответ модели на операции памяти. Строгий JSON: нет массива
 * add/update/delete или это не JSON — прогон считается неудачным.
 * Отдельные некорректные операции отбрасываются, чтобы одна ошибка модели
 * не портила остальную память.
 */
@Component
public class MemoryExtractionParser {
    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractionParser.class);
    private static final int DEFAULT_IMPORTANCE = 5;
    private static final String DEFAULT_CATEGORY = "другое";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryExtraction parse(String raw) {
        String json = stripFences(raw);
        if (json.isEmpty())
            throw new IllegalArgumentException("Модель вернула пустой ответ анализа");

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ответ модели не является JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject())
            throw new IllegalArgumentException("Ответ модели не является JSON-объектом");

        return new MemoryExtraction(
                drafts(root, "add", false),
                drafts(root, "update", true),
                deletes(root));
    }

    private List<MemoryDraft> drafts(JsonNode root, String field, boolean withId) {
        List<MemoryDraft> result = new ArrayList<>();
        for (JsonNode node : array(root, field)) {
            if (!node.isObject()) {
                logger.warn("Пропущена операция {}: элемент не объект", field);
                continue;
            }
            String content = text(node, "content");
            if (content.isBlank()) {
                logger.warn("Пропущена операция {} без текста", field);
                continue;
            }
            Long id = null;
            if (withId) {
                id = longValue(node.get("id"));
                if (id == null) {
                    logger.warn("Пропущено обновление без id: {}", content);
                    continue;
                }
            }
            result.add(new MemoryDraft(id, category(node), content, importance(node), eventDate(node), people(node)));
        }
        return result;
    }

    private List<Long> deletes(JsonNode root) {
        List<Long> result = new ArrayList<>();
        for (JsonNode node : array(root, "delete")) {
            Long id = node.isObject() ? longValue(node.get("id")) : longValue(node);
            if (id == null) {
                logger.warn("Пропущено удаление без id");
                continue;
            }
            result.add(id);
        }
        return result;
    }

    private JsonNode array(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray())
            throw new IllegalArgumentException("В ответе модели нет массива " + field);
        return node;
    }

    private String category(JsonNode node) {
        String category = text(node, "category");
        return category.isBlank() ? DEFAULT_CATEGORY : category;
    }

    private int importance(JsonNode node) {
        JsonNode value = node.get("importance");
        if (value == null || !value.canConvertToInt())
            return DEFAULT_IMPORTANCE;
        return Math.max(1, Math.min(10, value.asInt()));
    }

    private LocalDate eventDate(JsonNode node) {
        String value = text(node, "event_date");
        if (value.isBlank() || "null".equalsIgnoreCase(value))
            return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            logger.warn("Некорректная дата события '{}': сохраняем без даты", value);
            return null;
        }
    }

    private List<String> people(JsonNode node) {
        JsonNode value = node.get("people");
        List<String> people = new ArrayList<>();
        if (value == null || !value.isArray())
            return people;

        for (JsonNode item : value) {
            String name = item.asText("").trim();
            while (name.startsWith("@"))
                name = name.substring(1);
            if (!name.isBlank())
                people.add(name);
        }
        return people;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull())
            return null;
        if (node.canConvertToLong())
            return node.asLong();
        try {
            return Long.parseLong(node.asText("").trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
