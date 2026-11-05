package org.example.admin;

import org.example.core.aichat.MemoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminMemoryValidatorTest {

    private final AdminMemoryValidator validator = new AdminMemoryValidator();
    private final Set<String> categories = Set.of("семья", "работа", "другое");

    @Test
    void acceptsValidCard() {
        AdminMemoryValidator.Result result = validator.validate(form(), List.of(1L, 2L), categories);

        assertFalse(result.hasErrors());
        assertEquals("играет в шахматы", result.details().content());
        assertEquals(7, result.details().importance());
        assertEquals(LocalDate.of(2024, 3, 21), result.details().eventDate());
        assertEquals(List.of(1L, 2L), result.details().personIds());
    }

    @Test
    void acceptsEmptyEventDateAndEmptyPeople() {
        AdminMemoryValidator.Result result = validator.validate(form("eventDate", ""), List.of(), categories);

        assertFalse(result.hasErrors());
        assertNull(result.details().eventDate());
        assertTrue(result.details().personIds().isEmpty());
    }

    @Test
    void rejectsEmptyContent() {
        assertTrue(validator.validate(form("content", "  "), List.of(), categories)
                .errors().containsKey("content"));
    }

    @Test
    void rejectsImportanceOutOfRange() {
        assertTrue(validator.validate(form("importance", "11"), List.of(), categories)
                .errors().containsKey("importance"));
        assertTrue(validator.validate(form("importance", "0"), List.of(), categories)
                .errors().containsKey("importance"));
    }

    @Test
    void rejectsNonNumericImportance() {
        assertTrue(validator.validate(form("importance", "важно"), List.of(), categories)
                .errors().containsKey("importance"));
    }

    @Test
    void rejectsUnknownCategory() {
        assertTrue(validator.validate(form("category", "хобби"), List.of(), categories)
                .errors().containsKey("category"));
    }

    @Test
    void rejectsBrokenEventDate() {
        assertTrue(validator.validate(form("eventDate", "21.03.2024"), List.of(), categories)
                .errors().containsKey("eventDate"));
    }

    @Test
    void returnsNoDetailsWhenThereAreErrors() {
        assertNull(validator.validate(form("importance", "99"), List.of(), categories).details());
    }

    private MemoryForm form(String... overrides) {
        Map<String, String> values = new java.util.HashMap<>(Map.of(
                "category", "работа",
                "content", "играет в шахматы",
                "importance", "7",
                "eventDate", "2024-03-21"));
        for (int i = 0; i + 1 < overrides.length; i += 2)
            values.put(overrides[i], overrides[i + 1]);

        return new MemoryForm(null, 1, values.get("category"), values.get("content"),
                values.get("importance"), values.get("eventDate"), false, "manual");
    }
}
