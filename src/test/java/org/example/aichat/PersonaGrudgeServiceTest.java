package org.example.aichat;

import org.example.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.example.settings.Settings.AI_GRUDGE_ANGER_DELTA;
import static org.example.settings.Settings.AI_GRUDGE_DECAY_PER_HOUR;
import static org.example.settings.Settings.AI_GRUDGE_INSULT_STRENGTH;
import static org.example.settings.Settings.AI_GRUDGE_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonaGrudgeServiceTest {

    private static final long CHAT_ID = 7L;
    private static final long USER_ID = 111L;
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Mock
    private PersonaGrudgeRepository repository;
    @Mock
    private SettingsService settings;

    private PersonaGrudgeService service;

    @BeforeEach
    void setUp() {
        service = new PersonaGrudgeService(repository, settings, Clock.fixed(NOW, ZoneOffset.UTC));
        when(settings.getInt(AI_GRUDGE_INSULT_STRENGTH, 2)).thenReturn(2);
        when(settings.getInt(AI_GRUDGE_DECAY_PER_HOUR, 1)).thenReturn(1);
        when(settings.getInt(AI_GRUDGE_THRESHOLD, 5)).thenReturn(5);
        when(settings.getInt(AI_GRUDGE_ANGER_DELTA, 2)).thenReturn(2);
    }

    @Test
    void firstInsultCreatesGrudge() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of());

        service.insult(CHAT_ID, USER_ID, "ты никто");

        verify(repository).upsert(CHAT_ID, USER_ID, 2, "ты никто");
    }

    @Test
    void repeatedInsultAddsToEffectiveStrength() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(USER_ID, 2, NOW, "@nick", "Ник")));

        service.insult(CHAT_ID, USER_ID, "опять");

        verify(repository).upsert(CHAT_ID, USER_ID, 4, "опять");
    }

    @Test
    void strengthIsCappedAtTen() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(USER_ID, 9, NOW, "@nick", "Ник")));

        service.insult(CHAT_ID, USER_ID, "ещё");

        verify(repository).upsert(CHAT_ID, USER_ID, 10, "ещё");
    }

    @Test
    void apologyRemovesGrudge() {
        service.forgive(CHAT_ID, USER_ID);

        verify(repository).delete(CHAT_ID, USER_ID);
    }

    @Test
    void grudgeDecaysWithTime() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(USER_ID, 5, NOW.minusSeconds(2 * 3600), "@nick", "Ник")));

        List<PersonaGrudgeService.Grudge> active = service.active(CHAT_ID);

        assertEquals(3, active.get(0).strength());
    }

    @Test
    void fullyDecayedGrudgeIsNotActive() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(USER_ID, 2, NOW.minusSeconds(5 * 3600), "@nick", "Ник")));

        assertTrue(service.active(CHAT_ID).isEmpty());
    }

    @Test
    void onlyGrudgesAboveThresholdAreTargets() {
        when(repository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(USER_ID, 6, NOW, "@nick", "Ник"),
                new PersonaGrudgeRepository.GrudgeRow(222L, 4, NOW, "@ann", "Анна")));

        List<PersonaGrudgeService.Grudge> targets = service.targeted(CHAT_ID);

        assertEquals(1, targets.size());
        assertEquals("@nick (Ник)", targets.get(0).title());
    }

    @Test
    void insultWithoutUserIdIsIgnored() {
        service.insult(CHAT_ID, null, "оскорбление");

        verify(repository, never()).upsert(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void settingsAreReadForBonuses() {
        assertEquals(2, service.insultStrength());
        assertEquals(2, service.angerDelta());
        assertEquals(1, service.decayPerHour());
        assertEquals(5, service.threshold());
    }
}
