package org.example.admin;

import org.example.config.BotProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.example.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.settings.Settings.AI_GRUDGE_ANGER_DELTA;
import static org.example.settings.Settings.AI_GRUDGE_DECAY_PER_HOUR;
import static org.example.settings.Settings.AI_GRUDGE_INSULT_STRENGTH;
import static org.example.settings.Settings.AI_GRUDGE_THRESHOLD;
import static org.example.settings.Settings.AI_HISTORY_SIZE;
import static org.example.settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.settings.Settings.AI_MEMORY_ANALYSIS_MAX_TOKENS;
import static org.example.settings.Settings.AI_MEMORY_ANALYSIS_MESSAGES;
import static org.example.settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.settings.Settings.AI_MODEL_KEY;
import static org.example.settings.Settings.AI_PLAN_MAX_TOKENS;
import static org.example.settings.Settings.AI_ROUTE_KEY;
import static org.example.settings.Settings.OWNER_CHAT_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingsValidatorTest {

    private final AdminSettingsValidator validator = new AdminSettingsValidator(new BotProperties(
            "1:dummy", null, new BotProperties.Ai(Map.of("deepseek", "sk-deep")), null, null));

    @Test
    void acceptsValidForm() {
        assertTrue(validator.validate(validForm()).isEmpty());
    }

    @Test
    void acceptsMemoryBlockOfZeroAndEmptyOwner() {
        Map<String, String> form = validForm();
        form.put(AI_MEMORY_PROMPT_SIZE, "0");
        form.put(OWNER_CHAT_ID, "");

        assertTrue(validator.validate(form).isEmpty());
    }

    @Test
    void rejectsTemperatureOutOfRange() {
        Map<String, String> form = validForm();
        form.put(AI_ANSWER_TEMPERATURE, "3");

        assertTrue(validator.validate(form).containsKey(AI_ANSWER_TEMPERATURE));
    }

    @Test
    void rejectsTemperatureThatIsNotANumber() {
        Map<String, String> form = validForm();
        form.put(AI_ANSWER_TEMPERATURE, "горячо");

        assertTrue(validator.validate(form).containsKey(AI_ANSWER_TEMPERATURE));
    }

    @Test
    void rejectsTokenLimitsOutsideRange() {
        Map<String, String> form = validForm();
        form.put(AI_MAX_TOKENS_ANSWER_QUESTION, "0");

        assertTrue(validator.validate(form).containsKey(AI_MAX_TOKENS_ANSWER_QUESTION));

        form.put(AI_MAX_TOKENS_ANSWER_QUESTION, "9000");
        assertTrue(validator.validate(form).containsKey(AI_MAX_TOKENS_ANSWER_QUESTION));
    }

    @Test
    void rejectsAnalysisLimitsOutsideRange() {
        Map<String, String> form = validForm();
        form.put(AI_MEMORY_ANALYSIS_MAX_TOKENS, "9000");
        form.put(AI_MEMORY_ANALYSIS_MESSAGES, "0");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AI_MEMORY_ANALYSIS_MAX_TOKENS));
        assertTrue(errors.containsKey(AI_MEMORY_ANALYSIS_MESSAGES));
    }

    @Test
    void rejectsZeroHistoryAndZeroAnalysisThreshold() {
        Map<String, String> form = validForm();
        form.put(AI_HISTORY_SIZE, "0");
        form.put(AI_MEMORY_ANALYZE_EVERY, "0");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AI_HISTORY_SIZE));
        assertTrue(errors.containsKey(AI_MEMORY_ANALYZE_EVERY));
    }

    @Test
    void rejectsEmptyModelAndEmptyAnalysisPrompt() {
        Map<String, String> form = validForm();
        form.put(AI_MODEL_KEY, " ");
        form.put(AI_MEMORY_EXTRACT_PROMPT, "");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AI_MODEL_KEY));
        assertTrue(errors.containsKey(AI_MEMORY_EXTRACT_PROMPT));
    }

    @Test
    void rejectsModelOutsideTheList() {
        Map<String, String> form = validForm();
        form.put(AI_MODEL_KEY, "gpt-4");

        assertTrue(validator.validate(form).containsKey(AI_MODEL_KEY));
    }

    @Test
    void rejectsSwitchToProviderWithoutKey() {
        Map<String, String> form = validForm();
        form.put(AI_MODEL_KEY, "grok-4.7");

        Map<String, String> errors = validator.validate(form, Map.of(AI_MODEL_KEY, "deepseek-flash"));

        assertTrue(errors.get(AI_MODEL_KEY).contains("BOT_AI_KEY_GROK"), errors.get(AI_MODEL_KEY));
    }

    @Test
    void keepsCurrentModelWithoutKeyWhenOtherFieldsChange() {
        Map<String, String> form = validForm();
        form.put(AI_MODEL_KEY, "grok-4.7");
        form.put(AI_ANSWER_TEMPERATURE, "1.2");

        assertTrue(validator.validate(form, Map.of(AI_MODEL_KEY, "grok-4.7")).isEmpty());
    }

    @Test
    void rejectsOwnerChatIdThatIsNotPositiveNumber() {
        Map<String, String> form = validForm();
        form.put(OWNER_CHAT_ID, "не-id");

        assertTrue(validator.validate(form).containsKey(OWNER_CHAT_ID));

        form.put(OWNER_CHAT_ID, "-5");
        assertTrue(validator.validate(form).containsKey(OWNER_CHAT_ID));
    }

    @Test
    void rejectsGrudgeValuesOutsideRange() {
        Map<String, String> form = validForm();
        form.put(AI_GRUDGE_INSULT_STRENGTH, "11");
        form.put(AI_GRUDGE_ANGER_DELTA, "-1");
        form.put(AI_GRUDGE_DECAY_PER_HOUR, "11");
        form.put(AI_GRUDGE_THRESHOLD, "11");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AI_GRUDGE_INSULT_STRENGTH));
        assertTrue(errors.containsKey(AI_GRUDGE_ANGER_DELTA));
        assertTrue(errors.containsKey(AI_GRUDGE_DECAY_PER_HOUR));
        assertTrue(errors.containsKey(AI_GRUDGE_THRESHOLD));
    }

    @Test
    void rejectsPlanTokenLimitOutsideRange() {
        Map<String, String> form = validForm();
        form.put(AI_PLAN_MAX_TOKENS, "0");

        assertTrue(validator.validate(form).containsKey(AI_PLAN_MAX_TOKENS));
    }

    @Test
    void ignoresKeysThatAreNotInTheForm() {
        assertTrue(validator.validate(new HashMap<>()).isEmpty());
        assertFalse(validator.validate(validForm()).containsKey("чужой-ключ"));
    }

    @Test
    void rejectsModelThatDoesNotExistAtTheChosenDoor() {
        Map<String, String> form = validForm();
        form.put(AI_ROUTE_KEY, "aitunnel");
        form.put(AI_MODEL_KEY, "claude-sonnet-5-5");

        assertTrue(validator.validate(form).containsKey(AI_MODEL_KEY));
    }

    @Test
    void acceptsSameModelThroughAnotherDoorWhenItsKeyIsPresent() {
        Map<String, String> form = validForm();
        form.put(AI_ROUTE_KEY, "aitunnel");
        form.put(AI_MODEL_KEY, "gemini-4-argon");

        assertTrue(validatorWith(Map.of("deepseek", "sk-deep", "aitunnel", "sk-tunnel")).validate(form).isEmpty());
    }

    @Test
    void reportsProviderModeErrorAtTheVisibleField() {
        Map<String, String> form = validForm();
        form.put(AdminModelForm.ACCESS_KEY, AdminModelForm.PROVIDER);
        form.put(AdminModelForm.PROVIDER_KEY, "aitunnel");
        form.put(AdminModelForm.DOOR_MODEL_KEY, "нет-такой");
        form.put(AI_MODEL_KEY, "нет-такой");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AdminModelForm.DOOR_MODEL_KEY));
        assertFalse(errors.containsKey(AI_MODEL_KEY));
    }

    @Test
    void rejectsSwitchToAggregatorWithoutKey() {
        Map<String, String> form = validForm();
        form.put(AI_ROUTE_KEY, "venice");
        form.put(AI_MODEL_KEY, "claude-sonnet-5-5");

        Map<String, String> errors = validatorWith(Map.of("deepseek", "sk-deep")).validate(form);

        assertTrue(errors.getOrDefault(AI_MODEL_KEY, "").contains("BOT_AI_KEY_VENICE"));
    }

    @Test
    void keepsStoredAggregatorModelWithoutKeyOutOfTheWay() {
        Map<String, String> form = validForm();
        form.put(AI_ROUTE_KEY, "venice");
        form.put(AI_MODEL_KEY, "claude-sonnet-5-5");

        Map<String, String> stored = Map.of(AI_ROUTE_KEY, "venice", AI_MODEL_KEY, "claude-sonnet-5-5");

        assertTrue(validatorWith(Map.of("deepseek", "sk-deep")).validate(form, stored).isEmpty());
    }

    private AdminSettingsValidator validatorWith(Map<String, String> keys) {
        return new AdminSettingsValidator(new BotProperties(
                "1:dummy", null, new BotProperties.Ai(keys), null, null));
    }

    static Map<String, String> validForm() {
        Map<String, String> form = new HashMap<>();
        form.put(AI_MODEL_KEY, "deepseek-flash");
        form.put(AI_ANSWER_TEMPERATURE, "1.8");
        form.put(AI_MAX_TOKENS_ANSWER_QUESTION, "1000");
        form.put(AI_PLAN_MAX_TOKENS, "1500");
        form.put(AI_GRUDGE_INSULT_STRENGTH, "2");
        form.put(AI_GRUDGE_ANGER_DELTA, "2");
        form.put(AI_GRUDGE_DECAY_PER_HOUR, "1");
        form.put(AI_GRUDGE_THRESHOLD, "5");
        form.put(AI_HISTORY_SIZE, "100");
        form.put(AI_MEMORY_PROMPT_SIZE, "30");
        form.put(AI_MEMORY_ANALYZE_EVERY, "50");
        form.put(AI_MEMORY_ANALYSIS_MESSAGES, "50");
        form.put(AI_MEMORY_ANALYSIS_MAX_TOKENS, "8000");
        form.put(AI_MEMORY_EXTRACT_PROMPT, "Ты пополняешь память персоны");
        form.put(OWNER_CHAT_ID, "270459384");
        return form;
    }
}
