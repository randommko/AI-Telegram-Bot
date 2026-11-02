package org.example.Admin;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.Settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.Settings.Settings.AI_MODEL_KEY;
import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.example.Settings.Settings.AI_PERSONA_LEAVE_TEMPLATE;
import static org.example.Settings.Settings.OWNER_CHAT_ID;

/** Проверка формы настроек: ошибки возвращаются по ключу настройки и показываются у поля. */
@Component
public class AdminSettingsValidator {
    static final float MAX_TEMPERATURE = 2f;
    static final int MAX_TOKENS_LIMIT = 8192;
    static final String NAME_PLACEHOLDER = "{name}";

    public Map<String, String> validate(Map<String, String> values) {
        Map<String, String> errors = new LinkedHashMap<>();

        requireText(values, AI_MODEL_KEY, "Модель не может быть пустой", errors);
        requireText(values, AI_MEMORY_EXTRACT_PROMPT, "Промпт анализа не может быть пустым", errors);
        decimalInRange(values, AI_ANSWER_TEMPERATURE, 0f, MAX_TEMPERATURE,
                "Температура должна быть числом от 0 до 2", errors);
        integerInRange(values, AI_MAX_TOKENS_ANSWER_QUESTION, 1, MAX_TOKENS_LIMIT,
                "Лимит токенов должен быть целым от 1 до 8192", errors);
        integerInRange(values, AI_HISTORY_SIZE, 1, Integer.MAX_VALUE,
                "Размер истории должен быть целым больше нуля", errors);
        integerInRange(values, AI_MEMORY_PROMPT_SIZE, 0, Integer.MAX_VALUE,
                "Размер блока памяти должен быть целым не меньше нуля", errors);
        integerInRange(values, AI_MEMORY_ANALYZE_EVERY, 1, Integer.MAX_VALUE,
                "Порог анализа должен быть целым больше нуля", errors);
        ownerChatId(values, errors);
        requirePlaceholder(values, AI_PERSONA_JOIN_TEMPLATE, "Шаблон входа должен содержать {name}", errors);
        requirePlaceholder(values, AI_PERSONA_LEAVE_TEMPLATE, "Шаблон выхода должен содержать {name}", errors);

        return errors;
    }

    private void requireText(Map<String, String> values, String key, String message, Map<String, String> errors) {
        if (!values.containsKey(key))
            return;
        String value = values.get(key);
        if (value == null || value.isBlank())
            errors.put(key, message);
    }

    private void decimalInRange(Map<String, String> values, String key, float min, float max,
                                String message, Map<String, String> errors) {
        if (!values.containsKey(key))
            return;
        try {
            float parsed = Float.parseFloat(values.get(key).trim());
            if (parsed < min || parsed > max)
                errors.put(key, message);
        } catch (NumberFormatException e) {
            errors.put(key, message);
        }
    }

    private void integerInRange(Map<String, String> values, String key, int min, int max,
                                String message, Map<String, String> errors) {
        if (!values.containsKey(key))
            return;
        try {
            int parsed = Integer.parseInt(values.get(key).trim());
            if (parsed < min || parsed > max)
                errors.put(key, message);
        } catch (NumberFormatException e) {
            errors.put(key, message);
        }
    }

    private void ownerChatId(Map<String, String> values, Map<String, String> errors) {
        if (!values.containsKey(OWNER_CHAT_ID))
            return;
        String value = values.get(OWNER_CHAT_ID);
        if (value == null || value.isBlank())
            return;

        try {
            if (Long.parseLong(value.trim()) <= 0)
                errors.put(OWNER_CHAT_ID, "Получатель уведомлений должен быть положительным числом Telegram ID");
        } catch (NumberFormatException e) {
            errors.put(OWNER_CHAT_ID, "Получатель уведомлений должен быть числом или пустым полем");
        }
    }

    private void requirePlaceholder(Map<String, String> values, String key, String message,
                                    Map<String, String> errors) {
        if (!values.containsKey(key))
            return;
        String value = values.get(key);
        if (value == null || !value.contains(NAME_PLACEHOLDER))
            errors.put(key, message);
    }
}
