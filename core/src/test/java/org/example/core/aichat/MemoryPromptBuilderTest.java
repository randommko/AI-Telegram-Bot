package org.example.core.aichat;

import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.example.core.settings.Settings.AI_MEMORY_EVENT_DECAY_DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryPromptBuilderTest {

    private static final String CORE_HEADER =
            "Что ты помнишь о себе и о людях в чате всегда (это незыблемо):";
    private static final String REGULAR_HEADER = "Что ты помнишь о себе и о людях в чате:";

    private final MemoryPromptBuilder builder = new MemoryPromptBuilder(noDecay());

    /** Затухание выключено: тесты проверяют формат, а не старение. */
    private static MemoryDecay noDecay() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.getSettingValue(AI_MEMORY_EVENT_DECAY_DAYS)).thenReturn("0");
        return new MemoryDecay(settings, Clock.systemUTC());
    }

    @Test
    void coreBlockGoesFirstAndIsFormatted() {
        String block = builder.build(
                List.of(new MemoryRecord(1, "семья", "родился 1988-04-12", 10, true,
                        LocalDate.of(1988, 4, 12), List.of("andrey"))),
                List.of(new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of())));

        String expectedCore = CORE_HEADER + "\n- [семья] родился 1988-04-12 (важно: 10, 1988-04-12, @andrey)";
        String expectedRegular = REGULAR_HEADER + "\n- [хобби] шахматы (важно: 6)";

        assertEquals(expectedCore + "\n\n" + expectedRegular, block);
    }

    @Test
    void coreHeaderIsSkippedWithoutCoreRecords() {
        String block = builder.build(List.of(),
                List.of(new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of())));

        assertFalse(block.contains(CORE_HEADER));
        assertTrue(block.startsWith(REGULAR_HEADER));
    }

    @Test
    void emptyMemoryGivesEmptyBlock() {
        assertEquals("", builder.build(List.of(), List.of()));
    }

    @Test
    void dateAndPeopleAppearOnlyWhenFilled() {
        String core = builder.build(
                List.of(new MemoryRecord(1, "семья", "есть брат", 9, true, null, List.of())), List.of());
        String regular = builder.build(List.of(),
                List.of(new MemoryRecord(2, "друзья", "дружит с @ann", 5, false,
                        LocalDate.of(2025, 11, 1), List.of("ann", "nick"))));

        assertEquals(CORE_HEADER + "\n- [семья] есть брат (важно: 9)", core);
        assertEquals(REGULAR_HEADER + "\n- [друзья] дружит с @ann (важно: 5, 2025-11-01, @ann, @nick)", regular);
    }
}
