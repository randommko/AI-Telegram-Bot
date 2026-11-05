package org.example.bot.aichat;

import org.example.core.aichat.EmotionService;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Срыв: условие, пауза и последствия. Число реплик и адресатов задаёт персона. */
@Component
public class HysteriaService {
    private static final Logger logger = LoggerFactory.getLogger(HysteriaService.class);

    private final EmotionService emotions;
    private final PersonaRepository personaRepository;
    private final Clock clock;

    public HysteriaService(EmotionService emotions, PersonaRepository personaRepository, Clock clock) {
        this.emotions = emotions;
        this.personaRepository = personaRepository;
        this.clock = clock;
    }

    /** Срыв возможен только на новом оскорблении, при агрессии не ниже порога и вне паузы. */
    public boolean shouldStart(PersonaSettings settings, PlanIntent intent, int aggression) {
        return intent == PlanIntent.INSULT
                && aggression >= settings.aggressionThreshold()
                && settings.hysteriaCooldownPassed(clock.instant());
    }

    /**
     * Записывает срыв и возвращает гнев к характеру. Обида остаётся:
     * её снимает только извинение, а не окончание срыва.
     */
    public void record(int personaId) {
        personaRepository.updateLastHysteriaAt(personaId, clock.instant());
        emotions.resetAngerToBase(personaId);
        logger.info("Срыв персоны {}: гнев возвращён к базовому значению", personaId);
    }
}
