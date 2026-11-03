package org.example.Admin;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Журнал хранит только изменившиеся поля и показывает их подписями. */
class ChangeJournalTest {

    @Test
    void equalValuesDoNotProduceAChange() {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        ChangeJournal.diff(before, after, "prompt", "Ты Юра", "Ты Юра");

        assertTrue(before.isEmpty());
        assertTrue(after.isEmpty());
    }

    @Test
    void changedValueKeepsOldAndNew() {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();

        ChangeJournal.diff(before, after, "prompt", "Ты Юра", "Ты Петя");

        assertEquals(Map.of("prompt", "Ты Юра"), before);
        assertEquals(Map.of("prompt", "Ты Петя"), after);
    }

    @Test
    void entryNamesChangedFieldsAndAllowsRestoreOnlyForPersona() {
        ChangeJournal.Entry entry = new ChangeJournal.Entry(5L, OffsetDateTime.now(), "vasya",
                ChangeJournal.PERSONA, 1, null, 1,
                Map.of("prompt", "старый"), Map.of("prompt", "новый"));

        assertEquals("prompt", entry.fieldsTitle());
        assertEquals("персона", entry.entityTitle());
        assertTrue(entry.restorable());
    }

    @Test
    void bindingEntriesAreShownButNotRestored() {
        ChangeJournal.Entry entry = new ChangeJournal.Entry(6L, OffsetDateTime.now(), "admin",
                ChangeJournal.BINDING, 2, -100L, 2,
                Map.of("personaId", "2"), Map.of("personaId", "3"));

        assertEquals("привязка к чату", entry.entityTitle());
        assertEquals("personaId", entry.fieldsTitle());
        assertFalse(entry.restorable());
    }

    @Test
    void entryWithoutValuesSaysNothingChanged() {
        ChangeJournal.Entry entry = new ChangeJournal.Entry(7L, OffsetDateTime.now(), "admin",
                ChangeJournal.ACCOUNT, 1, null, null, Map.of(), Map.of());

        assertEquals("без изменений", entry.fieldsTitle());
        assertFalse(entry.restorable());
    }

    @Test
    void fieldsOfBothSidesAreListedOnce() {
        ChangeJournal.Entry entry = new ChangeJournal.Entry(8L, OffsetDateTime.now(), "vasya",
                ChangeJournal.PERSONA, 1, null, 1,
                Map.of("name", "Юра"), Map.of("prompt", "новый", "name", "Юра"));

        assertTrue(entry.fieldsTitle().contains("name"));
        assertTrue(entry.fieldsTitle().contains("prompt"));
        assertEquals(entry.fieldsTitle().indexOf("name"), entry.fieldsTitle().lastIndexOf("name"),
                "имя поля не должно дублироваться");
    }
}
