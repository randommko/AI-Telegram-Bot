package org.example.AiChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Разбирает ответ модели на шаге «план». Модель любит оборачивать JSON в markdown,
 * поэтому обёртка снимается заранее, а любой сбой разбора означает фолбэк.
 */
@Component
public class AnswerPlanParser {
    private static final Logger logger = LoggerFactory.getLogger(AnswerPlanParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<AnswerPlan> parse(String raw, int maxReplies) {
        if (raw == null || raw.isBlank())
            return Optional.empty();

        try {
            JsonNode root = objectMapper.readTree(jsonBody(raw));
            if (root == null || !root.isObject())
                return failed("ответ плана не объект");

            Optional<PlanIntent> intent = PlanIntent.from(root.path("intent").asText(null));
            if (intent.isEmpty())
                return failed("неизвестное намерение: " + root.path("intent").asText(""));

            int replies = intent.get() == PlanIntent.IGNORE ? 0 : root.path("replies").asInt(1);
            if (replies < 0 || replies > maxReplies || (intent.get() != PlanIntent.IGNORE && replies == 0))
                return failed("число реплик вне диапазона: " + replies);

            AnswerPlan plan = new AnswerPlan(
                    root.path("topic").asText("").trim(),
                    intent.get(),
                    integers(root.path("memory_ids")),
                    deltas(root.path("emotion")),
                    root.path("emotion_reason").asText("").trim(),
                    replies,
                    strings(root.path("targets")));
            return Optional.of(plan);
        } catch (Exception e) {
            logger.warn("План ответа не разобран: {}", String.valueOf(e));
            return Optional.empty();
        }
    }

    /** Снимает markdown-обёртку и служебный текст вокруг JSON-объекта. */
    static String jsonBody(String raw) {
        String text = raw.strip();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start < 0 ? fence + 3 : start);
            if (start >= 0 && end > start)
                text = text.substring(start + 1, end).strip();
        }

        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open >= 0 && close > open)
            return text.substring(open, close + 1);
        return text;
    }

    private Optional<AnswerPlan> failed(String reason) {
        logger.warn("План ответа не принят: {}", reason);
        return Optional.empty();
    }

    private static List<Integer> integers(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (element.canConvertToInt() && element.asInt() > 0)
                    values.add(element.asInt());
            }
        }
        return values;
    }

    private static Map<String, Integer> deltas(JsonNode node) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNumber())
                    values.put(entry.getKey(), entry.getValue().asInt());
            });
        }
        return values;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                String value = element.asText("").trim();
                if (!value.isEmpty())
                    values.add(value);
            }
        }
        return values;
    }
}
