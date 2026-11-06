package org.example.core.aichat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryDecayTest {

    private static final Instant NOW = Instant.parse("2026-11-30T12:00:00Z");

    @Test
    void eventLosesOnePointEveryFewDays() {
        LocalDate eventDate = LocalDate.of(2026, 11, 20);

        assertEquals(7, MemoryDecay.effective(10, eventDate, NOW, 3, 1, NOW));
    }

    @Test
    void eventDoesNotFallBelowOne() {
        LocalDate eventDate = LocalDate.of(2026, 1, 1);

        assertEquals(1, MemoryDecay.effective(5, eventDate, NOW, 3, 1, NOW));
    }

    @Test
    void zeroSpeedDisablesDecay() {
        LocalDate eventDate = LocalDate.of(2026, 1, 1);

        assertEquals(8, MemoryDecay.effective(8, eventDate, NOW, 0, 1, NOW));
    }

    @Test
    void missingEventDateFallsBackToCreationMoment() {
        Instant created = Instant.parse("2026-11-17T12:00:00Z");

        assertEquals(6, MemoryDecay.effective(10, null, created, 3, 1, NOW));
    }

    @Test
    void eventInTheFutureDoesNotGainPoints() {
        LocalDate tomorrow = LocalDate.of(2026, 12, 1);

        assertEquals(10, MemoryDecay.effective(10, tomorrow, NOW, 3, 1, NOW));
    }

    @Test
    void thresholdKeepsEventAboveFloor() {
        LocalDate longAgo = LocalDate.of(2026, 1, 1);

        assertEquals(3, MemoryDecay.effective(10, longAgo, NOW, 1, 3, NOW));
    }

    @Test
    void thresholdDoesNotRaiseAboveBase() {
        LocalDate longAgo = LocalDate.of(2026, 1, 1);

        assertEquals(2, MemoryDecay.effective(2, longAgo, NOW, 1, 5, NOW));
    }

    @Test
    void onlyEventsDecay() {
        MemoryDecay decay = new MemoryDecay(null, null);
        MemoryRecord friend = new MemoryRecord(1, "друзья", "Ваня — друг", 9, false, null, List.of());
        MemoryRecord core = new MemoryRecord(2, "события", "родился", 10, true,
                LocalDate.of(1988, 4, 12), List.of());

        assertEquals(9, decay.effective(friend, 3, 1, NOW));
        assertEquals(10, decay.effective(core, 3, 1, NOW));
    }
}
