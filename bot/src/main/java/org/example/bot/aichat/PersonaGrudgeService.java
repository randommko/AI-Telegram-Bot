package org.example.bot.aichat;

import org.example.core.aichat.AuthorFormat;
import org.example.core.aichat.GrudgeStrength;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.PersonaGrudgeRepository;

import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.example.core.settings.Settings.AI_GRUDGE_ANGER_DELTA;
import static org.example.core.settings.Settings.AI_GRUDGE_DECAY_PER_HOUR;
import static org.example.core.settings.Settings.AI_GRUDGE_INSULT_STRENGTH;
import static org.example.core.settings.Settings.AI_GRUDGE_THRESHOLD;

/**
 * Обиды считаются при чтении, как эмоции: в базе лежит сила на момент updated_at,
 * действующая сила уменьшается со временем и не зависит от затухания настроения.
 */
@Component
public class PersonaGrudgeService {
    private static final Logger logger = LoggerFactory.getLogger(PersonaGrudgeService.class);

    static final int DEFAULT_INSULT_STRENGTH = 2;
    static final int DEFAULT_ANGER_DELTA = 2;
    static final int DEFAULT_DECAY_PER_HOUR = 1;
    static final int DEFAULT_THRESHOLD = 5;
    static final int MAX_STRENGTH = 10;

    public record Grudge(long userId, String title, int strength) {
    }

    private final PersonaGrudgeRepository repository;
    private final SettingsService settings;
    private final Clock clock;

    public PersonaGrudgeService(PersonaGrudgeRepository repository, SettingsService settings, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
    }

    /** Действующие обиды чата: сила уже уменьшена на прошедшее время. */
    public List<Grudge> active(long chatId) {
        Instant now = clock.instant();
        int decay = decayPerHour();
        List<Grudge> active = new ArrayList<>();

        for (PersonaGrudgeRepository.GrudgeRow row : repository.findForChat(chatId)) {
            int strength = effective(row.strength(), row.updatedAt(), now, decay);
            if (strength > 0)
                active.add(new Grudge(row.userId(), title(row), strength));
        }
        return active;
    }

    /** Обиды, которые доросли до порога адресата колкостей. */
    public List<Grudge> targeted(long chatId) {
        int threshold = threshold();
        return active(chatId).stream().filter(grudge -> grudge.strength() >= threshold).toList();
    }

    public void insult(long chatId, Long userId, String offence) {
        if (userId == null)
            return;

        int current = active(chatId).stream()
                .filter(grudge -> grudge.userId() == userId)
                .mapToInt(Grudge::strength)
                .findFirst()
                .orElse(0);
        int updated = Math.min(MAX_STRENGTH, current + insultStrength());
        repository.upsert(chatId, userId, updated, offence);
        logger.info("Обида в чате {} на участника {} выросла до {}", chatId, userId, updated);
    }

    public void forgive(long chatId, Long userId) {
        if (userId == null)
            return;
        repository.delete(chatId, userId);
        logger.info("Обида в чате {} на участника {} снята извинением", chatId, userId);
    }

    public int angerDelta() {
        return settings.getInt(AI_GRUDGE_ANGER_DELTA, DEFAULT_ANGER_DELTA);
    }

    public int insultStrength() {
        return settings.getInt(AI_GRUDGE_INSULT_STRENGTH, DEFAULT_INSULT_STRENGTH);
    }

    public int decayPerHour() {
        return settings.getInt(AI_GRUDGE_DECAY_PER_HOUR, DEFAULT_DECAY_PER_HOUR);
    }

    public int threshold() {
        return settings.getInt(AI_GRUDGE_THRESHOLD, DEFAULT_THRESHOLD);
    }

    /** Сила обиды на момент now: линейно падает, не уходя ниже нуля. */
    static int effective(int strength, Instant updatedAt, Instant now, int decayPerHour) {
        return GrudgeStrength.effective(strength, updatedAt, now, decayPerHour);
    }

    private static String title(PersonaGrudgeRepository.GrudgeRow row) {
        String title = AuthorFormat.sign(new MessageAuthor(row.userId(), row.nick(), row.name()));
        return title.isEmpty() ? "участник " + row.userId() : title;
    }
}
