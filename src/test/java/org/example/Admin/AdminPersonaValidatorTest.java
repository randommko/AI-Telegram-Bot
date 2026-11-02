package org.example.Admin;

import org.example.AiChat.PersonaRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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

    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final AdminPersonaValidator validator = new AdminPersonaValidator(personas);

    @Test
    void acceptsValidCard() {
        AdminPersonaValidator.Result result = validator.validate(form());

        assertFalse(result.hasErrors());
        assertEquals("Юра", result.details().name());
        assertEquals(List.of("юр", "юра"), result.details().aliases());
        assertEquals(8, result.details().angerThreshold());
    }

    @Test
    void rejectsEmptyNameAndPrompt() {
        Map<String, String> errors = validator.validate(form("name", " ", "prompt", "")).errors();

        assertTrue(errors.containsKey("name"));
        assertTrue(errors.containsKey("prompt"));
    }

    @Test
    void rejectsNameTakenByAnotherPersona() {
        when(personas.nameTaken(eq("Юра"), any())).thenReturn(true);

        assertTrue(validator.validate(form()).errors().containsKey("name"));
    }

    @Test
    void checksNameAgainstOwnIdWhenEditing() {
        validator.validate(form("id", "7"));

        verify(personas).nameTaken("Юра", 7);
    }

    @Test
    void rejectsAliasEqualToCommand() {
        assertTrue(validator.validate(form("aliases", "юр\nclear")).errors().get("aliases")
                .contains("командой"));
        assertTrue(validator.validate(form("aliases", "summary")).errors().containsKey("aliases"));
        assertTrue(validator.validate(form("aliases", "ai")).errors().containsKey("aliases"));
    }

    @Test
    void acceptsPersonaAddressNameAsAlias() {
        assertFalse(validator.validate(form("aliases", "юра")).hasErrors());
        assertFalse(validator.validate(form("aliases", "юрец")).hasErrors());
    }

    @Test
    void rejectsAliasWithSpaces() {
        assertTrue(validator.validate(form("aliases", "юра великий")).errors().containsKey("aliases"));
    }

    @Test
    void rejectsAliasWithoutAnyAliases() {
        assertTrue(validator.validate(form("aliases", " ")).errors().containsKey("aliases"));
    }

    @Test
    void rejectsAliasOwnedByAnotherPersona() {
        when(personas.aliasOwner(eq("юр"), any())).thenReturn(Optional.of(9));

        assertTrue(validator.validate(form("aliases", "юр")).errors().containsKey("aliases"));
    }

    @Test
    void rejectsScaleAboveTen() {
        Map<String, String> errors = validator.validate(form("baseJoy", "11")).errors();

        assertTrue(errors.containsKey("baseJoy"));
    }

    @Test
    void rejectsTooShortReplyDelay() {
        assertTrue(validator.validate(form("replyDelayMs", "100")).errors().containsKey("replyDelayMs"));
    }

    @Test
    void rejectsRepliesOutsideHysteriaRange() {
        assertTrue(validator.validate(form("hysteriaReplies", "2")).errors().containsKey("hysteriaReplies"));
        assertTrue(validator.validate(form("maxReplies", "7")).errors().containsKey("maxReplies"));
    }

    @Test
    void rejectsNonNumericScale() {
        assertTrue(validator.validate(form("baseAnger", "злой")).errors().containsKey("baseAnger"));
    }

    @Test
    void returnsNoDetailsWhenThereAreErrors() {
        assertNull(validator.validate(form("baseJoy", "99")).details());
    }

    @Test
    void parsesAliasesFromLinesAndCommas() {
        assertEquals(List.of("юр", "юра", "юрец"), validator.parseAliases("юр, юра\nюрец\n\n"));
        assertEquals(List.of("юр"), validator.parseAliases("юр, ЮР"));
    }

    @Test
    void forbidsPlainCommandsButNotPersonaNames() {
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("clear"));
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("summary"));
        assertTrue(AdminPersonaValidator.forbiddenCommands().contains("ai"));
        assertFalse(AdminPersonaValidator.forbiddenCommands().contains("юра"));
        assertFalse(AdminPersonaValidator.forbiddenCommands().contains("юрец"));
    }

    /** Карточка с валидными значениями; переопределения идут парами «поле, значение». */
    private PersonaForm form(String... overrides) {
        Map<String, String> values = new HashMap<>(Map.ofEntries(
                Map.entry("name", "Юра"),
                Map.entry("prompt", "Ты Юра"),
                Map.entry("aliases", "юр, юра"),
                Map.entry("baseJoy", "5"),
                Map.entry("baseAnger", "2"),
                Map.entry("baseOffence", "0"),
                Map.entry("baseFatigue", "3"),
                Map.entry("baseSarcasm", "5"),
                Map.entry("offenceDecayPerHour", "1"),
                Map.entry("angerThreshold", "8"),
                Map.entry("hysteriaReplies", "4"),
                Map.entry("hysteriaCooldownHours", "6"),
                Map.entry("maxReplies", "4"),
                Map.entry("replyDelayMs", "1500"),
                Map.entry("initiativeEnabled", "true"),
                Map.entry("initiativeCooldownMessages", "20")));

        for (int i = 0; i + 1 < overrides.length; i += 2)
            values.put(overrides[i], overrides[i + 1]);

        String id = values.get("id");
        return new PersonaForm(
                id == null ? null : Integer.valueOf(id),
                values.get("name"),
                values.get("prompt"),
                values.get("aliases"),
                values.get("baseJoy"),
                values.get("baseAnger"),
                values.get("baseOffence"),
                values.get("baseFatigue"),
                values.get("baseSarcasm"),
                values.get("offenceDecayPerHour"),
                values.get("angerThreshold"),
                values.get("hysteriaReplies"),
                values.get("hysteriaCooldownHours"),
                values.get("maxReplies"),
                values.get("replyDelayMs"),
                Boolean.parseBoolean(values.get("initiativeEnabled")),
                values.get("initiativeCooldownMessages"));
    }
}
