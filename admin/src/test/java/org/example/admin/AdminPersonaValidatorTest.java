package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionRepository;
import org.example.core.aichat.PersonaRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPersonaValidatorTest {

    private static final List<Emotion> CATALOG = List.of(
            new Emotion("радость", "Радость", "Подъём, желание шутить", "плюс", -0.25, 1.00, 5, false, 10),
            new Emotion("гнев", "Гнев", "Злость и резкость", "минус", 0.55, 1.50, 2, false, 30),
            new Emotion("сарказм", "Сарказм", "Ирония как манера речи", "стиль", 0.15, 0.50, 5, false, 110),
            new Emotion("агрессия", "Агрессия", "Производная шкала", "стиль", 0, 0, 0, true, 999));

    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final EmotionRepository emotions = mock(EmotionRepository.class);
    private final AdminPersonaValidator validator = new AdminPersonaValidator(personas, emotions);

    @Test
    void acceptsValidCard() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        AdminPersonaValidator.Result result = validator.validate(form());

        assertFalse(result.hasErrors());
        assertEquals("Юра", result.details().name());
        assertEquals(List.of("юр", "юра"), result.details().aliases());
        assertEquals(8, result.details().aggressionThreshold());
        assertEquals(2, result.details().emotionBaseValues().get("гнев"));
        assertFalse(result.details().emotionBaseValues().containsKey("агрессия"),
                "производная шкала значения у персоны не имеет");
    }

    @Test
    void rejectsEmptyNameAndPrompt() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        Map<String, String> errors = validator.validate(form("name", " ", "prompt", "")).errors();

        assertTrue(errors.containsKey("name"));
        assertTrue(errors.containsKey("prompt"));
    }

    @Test
    void rejectsNameTakenByAnotherPersona() {
        when(personas.nameTaken(eq("Юра"), any())).thenReturn(true);
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form()).errors().containsKey("name"));
    }

    @Test
    void checksNameAgainstOwnIdWhenEditing() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        validator.validate(form("id", "7"));

        verify(personas).nameTaken("Юра", 7);
    }

    @Test
    void rejectsAliasEqualToCommand() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("aliases", "юр\nclear")).errors().get("aliases")
                .contains("командой"));
        assertTrue(validator.validate(form("aliases", "bot_info")).errors().containsKey("aliases"));
        assertTrue(validator.validate(form("aliases", "ai")).errors().containsKey("aliases"));
    }

    @Test
    void acceptsAliasThatIsNotACommand() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertFalse(validator.validate(form("aliases", "юра")).hasErrors());
        assertFalse(validator.validate(form("aliases", "юрий")).hasErrors());
        assertFalse(validator.validate(form("aliases", "юрец")).hasErrors());
    }

    @Test
    void rejectsAliasWithSpaces() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("aliases", "юра великий")).errors().containsKey("aliases"));
    }

    @Test
    void rejectsAliasWithoutAnyAliases() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("aliases", " ")).errors().containsKey("aliases"));
    }

    @Test
    void acceptsAliasOwnedByAnotherPersona() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertFalse(validator.validate(form("aliases", "юр")).errors().containsKey("aliases"));
    }

    @Test
    void rejectsScaleAboveTen() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        Map<String, String> errors = validator.validate(form("emotion.радость", "11")).errors();

        assertTrue(errors.containsKey("emotion.радость"));
        assertEquals("Радость: допустимо от 0 до 10", errors.get("emotion.радость"));
    }

    @Test
    void acceptsZeroAndTen() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertFalse(validator.validate(form("emotion.радость", "0", "emotion.гнев", "10")).hasErrors());
    }

    @Test
    void rejectsNonNumericScale() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("emotion.гнев", "злой")).errors().containsKey("emotion.гнев"));
    }

    @Test
    void rejectsEmotionMissingFromTheForm() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        PersonaForm empty = form();
        empty.emotionValues().remove("гнев");

        assertTrue(validator.validate(empty).errors().containsKey("emotion.гнев"));
    }

    @Test
    void rejectsTooShortReplyDelay() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("replyDelayMs", "100")).errors().containsKey("replyDelayMs"));
    }

    @Test
    void rejectsRepliesOutsideHysteriaRange() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertTrue(validator.validate(form("hysteriaReplies", "2")).errors().containsKey("hysteriaReplies"));
        assertTrue(validator.validate(form("maxReplies", "7")).errors().containsKey("maxReplies"));
    }

    @Test
    void returnsNoDetailsWhenThereAreErrors() {
        when(emotions.findCatalog()).thenReturn(CATALOG);

        assertNull(validator.validate(form("emotion.радость", "99")).details());
    }

    @Test
    void blankFormUsesCatalogDefaultsAndSkipsDerivedScales() {
        PersonaForm blank = PersonaForm.blank(CATALOG);

        assertEquals(3, blank.emotionValues().size());
        assertEquals("5", blank.emotionValues().get("радость"));
        assertEquals("2", blank.emotionValues().get("гнев"));
        assertFalse(blank.emotionValues().containsKey("агрессия"));
    }

    @Test
    void parsesAliasesFromLinesAndCommas() {
        assertEquals(List.of("юр", "юра", "юрец"), validator.parseAliases("юр, юра\nюрец\n\n"));
        assertEquals(List.of("юр"), validator.parseAliases("юр, ЮР"));
    }

    @Test
    void forbidsPlainCommandsButNotPersonaNames() {
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("clear"));
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("bot_info"));
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("help"));
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("ai"));
        assertFalse(AdminPersonaValidator.forbiddenCommands().contains("summary"));
        assertFalse(AdminPersonaValidator.forbiddenCommands().contains("юра"));
        assertFalse(AdminPersonaValidator.forbiddenCommands().contains("юрец"));
    }

    /** Карточка с валидными значениями; переопределения идут парами «поле, значение». */
    private PersonaForm form(String... overrides) {
        Map<String, String> emotionValues = new LinkedHashMap<>();
        emotionValues.put("радость", "5");
        emotionValues.put("гнев", "2");
        emotionValues.put("сарказм", "5");

        Map<String, String> values = new HashMap<>(Map.ofEntries(
                Map.entry("name", "Юра"),
                Map.entry("prompt", "Ты Юра"),
                Map.entry("aliases", "юр, юра"),
                Map.entry("aggressionThreshold", "8"),
                Map.entry("hysteriaReplies", "4"),
                Map.entry("hysteriaCooldownHours", "6"),
                Map.entry("maxReplies", "4"),
                Map.entry("replyDelayMs", "1500"),
                Map.entry("initiativeEnabled", "true"),
                Map.entry("initiativeCooldownMessages", "20")));

        for (int i = 0; i + 1 < overrides.length; i += 2) {
            String field = overrides[i];
            if (field.startsWith(AdminPersonaValidator.EMOTION_FIELD_PREFIX))
                emotionValues.put(field.substring(AdminPersonaValidator.EMOTION_FIELD_PREFIX.length()),
                        overrides[i + 1]);
            else
                values.put(field, overrides[i + 1]);
        }

        String id = values.get("id");
        return new PersonaForm(
                id == null ? null : Integer.valueOf(id),
                values.get("name"),
                values.get("prompt"),
                values.get("aliases"),
                emotionValues,
                values.get("aggressionThreshold"),
                values.get("hysteriaReplies"),
                values.get("hysteriaCooldownHours"),
                values.get("maxReplies"),
                values.get("replyDelayMs"),
                Boolean.parseBoolean(values.get("initiativeEnabled")),
                values.get("initiativeCooldownMessages"));
    }
}
