package org.example.bot.aichat;

import org.example.core.aichat.EmotionService;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HysteriaServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final int PERSONA_ID = 3;

    @Mock
    private EmotionService emotions;
    @Mock
    private PersonaRepository personaRepository;

    private HysteriaService service;

    @BeforeEach
    void setUp() {
        service = new HysteriaService(emotions, personaRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void hysteriaStartsAtThresholdWithInsult() {
        assertTrue(service.shouldStart(settings(null), PlanIntent.INSULT, 8));
    }

    @Test
    void aggressionBelowThresholdBlocksHysteria() {
        assertFalse(service.shouldStart(settings(null), PlanIntent.INSULT, 7));
    }

    @Test
    void remarkDoesNotStartHysteria() {
        assertFalse(service.shouldStart(settings(null), PlanIntent.REMARK, 10));
    }

    @Test
    void cooldownBlocksHysteria() {
        assertFalse(service.shouldStart(settings(NOW.minusSeconds(3600)), PlanIntent.INSULT, 10));
    }

    @Test
    void recordWritesTimeAndResetsAnger() {
        service.record(PERSONA_ID);

        verify(personaRepository).updateLastHysteriaAt(PERSONA_ID, NOW);
        verify(emotions).resetAngerToBase(PERSONA_ID);
    }

    private static PersonaSettings settings(Instant lastHysteria) {
        return new PersonaSettings(PERSONA_ID, 4, 1500, 8, 4, 6, lastHysteria);
    }
}
