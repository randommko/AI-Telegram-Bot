package org.example.AiChat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonaSettingsTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void firstHysteriaIsAlwaysAllowed() {
        assertTrue(settings(null).hysteriaCooldownPassed(NOW));
    }

    @Test
    void cooldownBlocksEarlyHysteria() {
        assertFalse(settings(NOW.minusSeconds(3600)).hysteriaCooldownPassed(NOW));
    }

    @Test
    void cooldownPassesAfterConfiguredHours() {
        assertTrue(settings(NOW.minusSeconds(6 * 3600)).hysteriaCooldownPassed(NOW));
    }

    private static PersonaSettings settings(Instant lastHysteria) {
        return new PersonaSettings(1, 4, 1500, 8, 4, 6, lastHysteria);
    }
}
