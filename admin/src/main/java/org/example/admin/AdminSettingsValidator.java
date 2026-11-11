package org.example.admin;

import org.example.core.aichat.LlmCatalog;
import org.example.core.aichat.ProviderKeyStatusRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.core.settings.Settings.AI_GRUDGE_ANGER_DELTA;
import static org.example.core.settings.Settings.AI_GRUDGE_DECAY_PER_HOUR;
import static org.example.core.settings.Settings.AI_GRUDGE_INSULT_STRENGTH;
import static org.example.core.settings.Settings.AI_GRUDGE_THRESHOLD;
import static org.example.core.settings.Settings.AI_HISTORY_SIZE;
import static org.example.core.settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYSIS_MAX_TOKENS;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYSIS_MESSAGES;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.core.settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.core.settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.core.settings.Settings.AI_MODEL_KEY;
import static org.example.core.settings.Settings.AI_PLAN_MAX_TOKENS;
import static org.example.core.settings.Settings.AI_REACTION_MAX_TOKENS;
import static org.example.core.settings.Settings.AI_REACTION_MODEL;
import static org.example.core.settings.Settings.AI_REACTION_PAUSE_SECONDS;
import static org.example.core.settings.Settings.AI_REACTION_POOL_SIZE;
import static org.example.core.settings.Settings.AI_REACTION_ROUTE;
import static org.example.core.settings.Settings.AI_ROUTE_KEY;
import static org.example.core.settings.Settings.OWNER_CHAT_ID;

/** Проверка формы настроек: ошибки возвращаются по ключу настройки и показываются у поля. */
@Component
public class AdminSettingsValidator {
    static final float MAX_TEMPERATURE = 2f;
    static final int MAX_TOKENS_LIMIT = 8192;
    static final int MAX_ANALYSIS_MESSAGES = 1000;

    private final ProviderKeyStatusRepository providerKeys;

    public AdminSettingsValidator(ProviderKeyStatusRepository providerKeys) {
        this.providerKeys = providerKeys;
    }

    public Map<String, String> validate(Map<String, String> values) {
        return validate(values, Map.of());
    }

    /**
     * Сохранённые значения нужны из-за правила ключа: переключиться на поставщика без ключа
     * нельзя, а уже выбранная модель без ключа не должна блокировать остальные поля вкладки.
     */
    public Map<String, String> validate(Map<String, String> values, Map<String, String> stored) {
        Map<String, String> errors = new LinkedHashMap<>();

        requireModel(values, AI_MODEL_KEY, AdminModelForm.ACCESS_KEY, AdminModelForm.DOOR_MODEL_KEY, errors);
        modelFromCatalog(values, stored, AI_MODEL_KEY, AI_ROUTE_KEY,
                AdminModelForm.ACCESS_KEY, AdminModelForm.DOOR_MODEL_KEY, errors);
        requireModel(values, AI_REACTION_MODEL, AdminModelForm.REACTION_ACCESS_KEY,
                AdminModelForm.REACTION_DOOR_MODEL_KEY, errors);
        modelFromCatalog(values, stored, AI_REACTION_MODEL, AI_REACTION_ROUTE,
                AdminModelForm.REACTION_ACCESS_KEY, AdminModelForm.REACTION_DOOR_MODEL_KEY, errors);
        integerInRange(values, AI_REACTION_POOL_SIZE, 1, 100,
                "Размер пула должен быть целым от 1 до 100", errors);
        integerInRange(values, AI_REACTION_PAUSE_SECONDS, 1, 3600,
                "Пауза должна быть целой от 1 до 3600 секунд", errors);
        integerInRange(values, AI_REACTION_MAX_TOKENS, 1, MAX_TOKENS_LIMIT,
                "Лимит токенов прохода должен быть целым от 1 до 8192", errors);
        requireText(values, AI_MEMORY_EXTRACT_PROMPT, "Промпт анализа не может быть пустым", errors);
        decimalInRange(values, AI_ANSWER_TEMPERATURE, 0f, MAX_TEMPERATURE,
                "Температура должна быть числом от 0 до 2", errors);
        integerInRange(values, AI_MAX_TOKENS_ANSWER_QUESTION, 1, MAX_TOKENS_LIMIT,
                "Лимит токенов должен быть целым от 1 до 8192", errors);
        integerInRange(values, AI_PLAN_MAX_TOKENS, 1, MAX_TOKENS_LIMIT,
                "Лимит токенов плана должен быть целым от 1 до 8192", errors);
        integerInRange(values, AI_GRUDGE_INSULT_STRENGTH, 0, 10,
                "Прибавка к обиде должна быть целой от 0 до 10", errors);
        integerInRange(values, AI_GRUDGE_ANGER_DELTA, 0, 10,
                "Прибавка к гневу должна быть целой от 0 до 10", errors);
        integerInRange(values, AI_GRUDGE_DECAY_PER_HOUR, 0, 10,
                "Затухание обиды должно быть целым от 0 до 10", errors);
        integerInRange(values, AI_GRUDGE_THRESHOLD, 0, 10,
                "Порог адресата должен быть целым от 0 до 10", errors);
        integerInRange(values, AI_MEMORY_ANALYSIS_MAX_TOKENS, 1, MAX_TOKENS_LIMIT,
                "Лимит токенов анализа должен быть целым от 1 до 8192", errors);
        integerInRange(values, AI_MEMORY_ANALYSIS_MESSAGES, 1, MAX_ANALYSIS_MESSAGES,
                "Число сообщений анализа должно быть целым от 1 до 1000", errors);
        integerInRange(values, AI_HISTORY_SIZE, 1, Integer.MAX_VALUE,
                "Размер истории должен быть целым больше нуля", errors);
        integerInRange(values, AI_MEMORY_PROMPT_SIZE, 0, Integer.MAX_VALUE,
                "Размер блока памяти должен быть целым не меньше нуля", errors);
        integerInRange(values, AI_MEMORY_ANALYZE_EVERY, 1, Integer.MAX_VALUE,
                "Порог анализа должен быть целым больше нуля", errors);
        ownerChatId(values, errors);

        return errors;
    }

    /** Модель должна существовать у провайдера из маршрута, а его ключ нужен при смене пары. */
    private void modelFromCatalog(Map<String, String> values, Map<String, String> stored,
                                  String modelKey, String routeKey, String accessKey, String doorKey,
                                  Map<String, String> errors) {
        String value = values.get(modelKey);
        if (value == null || value.isBlank())
            return;

        String route = values.getOrDefault(routeKey, "").trim();
        String errorKey = modelErrorKey(values, accessKey, doorKey, modelKey);

        Optional<LlmCatalog.Model> found = LlmCatalog.model(route, value);
        if (found.isEmpty()) {
            errors.put(errorKey, "Модели «" + value.trim() + "» нет в списке доступных");
            return;
        }

        String previousRoute = stored.getOrDefault(routeKey, "").trim();
        String previousModel = stored.getOrDefault(modelKey, "").trim();
        Optional<LlmCatalog.Model> before = LlmCatalog.model(previousRoute, previousModel);
        boolean sameDoorAndModel = before
                .map(model -> model.id().equals(found.get().id())
                        && model.providerId().equals(found.get().providerId()))
                .orElse(false);
        if (sameDoorAndModel)
            return;

        LlmCatalog.Provider provider = LlmCatalog.providerOf(found.get()).orElse(null);
        if (provider != null && !providerKeys.hasKey(provider.id()))
            errors.put(errorKey, "У поставщика " + provider.title() + " нет ключа ("
                    + provider.envName() + "): выберите модель другого поставщика");
    }

    /** Ошибка модели показывается у того поля, которое видно в выбранном режиме. */
    private String modelErrorKey(Map<String, String> values, String accessKey, String doorKey, String modelKey) {
        return AdminModelForm.PROVIDER.equals(values.getOrDefault(accessKey, "")) ? doorKey : modelKey;
    }

    private void requireModel(Map<String, String> values, String modelKey,
                              String accessKey, String doorKey, Map<String, String> errors) {
        if (!values.containsKey(modelKey))
            return;

        String value = values.get(modelKey);
        if (value == null || value.isBlank())
            errors.put(modelErrorKey(values, accessKey, doorKey, modelKey), "Модель не может быть пустой");
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
}
