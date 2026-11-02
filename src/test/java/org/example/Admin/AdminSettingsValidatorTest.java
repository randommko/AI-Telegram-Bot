package org.example.Admin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSettingsValidatorTest {

    private final AdminSettingsValidator validator = new AdminSettingsValidator();

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
    void rejectsOwnerChatIdThatIsNotPositiveNumber() {
        Map<String, String> form = validForm();
        form.put(OWNER_CHAT_ID, "не-id");

        assertTrue(validator.validate(form).containsKey(OWNER_CHAT_ID));

        form.put(OWNER_CHAT_ID, "-5");
        assertTrue(validator.validate(form).containsKey(OWNER_CHAT_ID));
    }

    @Test
    void rejectsTemplateWithoutNamePlaceholder() {
        Map<String, String> form = validForm();
        form.put(AI_PERSONA_JOIN_TEMPLATE, "В чат приходит гость");
        form.put(AI_PERSONA_LEAVE_TEMPLATE, "покинул чат");

        Map<String, String> errors = validator.validate(form);

        assertTrue(errors.containsKey(AI_PERSONA_JOIN_TEMPLATE));
        assertTrue(errors.containsKey(AI_PERSONA_LEAVE_TEMPLATE));
    }

    @Test
    void ignoresKeysThatAreNotInTheForm() {
        assertTrue(validator.validate(new HashMap<>()).isEmpty());
        assertFalse(validator.validate(validForm()).containsKey("чужой-ключ"));
    }

    static Map<String, String> validForm() {
        Map<String, String> form = new HashMap<>();
        form.put(AI_MODEL_KEY, "deepseek-flash");
        form.put(AI_ANSWER_TEMPERATURE, "1.8");
        form.put(AI_MAX_TOKENS_ANSWER_QUESTION, "1000");
        form.put(AI_HISTORY_SIZE, "100");
        form.put(AI_MEMORY_PROMPT_SIZE, "30");
        form.put(AI_MEMORY_ANALYZE_EVERY, "50");
        form.put(AI_MEMORY_EXTRACT_PROMPT, "Ты пополняешь память персоны");
        form.put(AI_PERSONA_JOIN_TEMPLATE, "В чат приходит {name}");
        form.put(AI_PERSONA_LEAVE_TEMPLATE, "{name} покинул чат");
        form.put(OWNER_CHAT_ID, "270459384");
        return form;
    }
}
