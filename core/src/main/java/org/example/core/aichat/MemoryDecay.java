package org.example.core.aichat;

import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.example.core.settings.Settings.AI_MEMORY_EVENT_DECAY_DAYS;

/**
 * Затухание важности событий: как у эмоций, значение считается при чтении,
 * планировщика нет. Друзья, семья, хобби и работа — факты, они не стареют.
 */
@Component
public class MemoryDecay {
    private static final Logger logger = LoggerFactory.getLogger(MemoryDecay.class);
    static final int DEFAULT_DAYS_PER_POINT = 3;
    static final int MAX_DAYS_PER_POINT = 365;
    /** Минимум шкалы важности: событие стареет до него и остаётся в памяти. */
    static final int MIN_IMPORTANCE = 1;

    private final SettingsService settings;
    private final Clock clock;

    public MemoryDecay(SettingsService settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** Сколько дней нужно, чтобы важность упала на балл; 0 — затухание выключено. */
    public int daysPerPoint() {
        String configured = settings.getSettingValue(AI_MEMORY_EVENT_DECAY_DAYS);
        if (configured == null || configured.isBlank())
            return DEFAULT_DAYS_PER_POINT;

        try {
            int days = Integer.parseInt(configured.trim());
            if (days >= 0 && days <= MAX_DAYS_PER_POINT)
                return days;
            logger.warn("Затухание важности {} вне диапазона 0..{}: используется {}",
                    days, MAX_DAYS_PER_POINT, DEFAULT_DAYS_PER_POINT);
        } catch (NumberFormatException e) {
            logger.warn("Некорректное затухание важности '{}': используется {}",
                    configured, DEFAULT_DAYS_PER_POINT);
        }
        return DEFAULT_DAYS_PER_POINT;
    }

    public int effective(MemoryRecord memory) {
        return effective(memory, daysPerPoint(), clock.instant());
    }

    /** Стареют только события: ядро и остальные типы — факты, их важность не меняется. */
    public int effective(MemoryRecord memory, int daysPerPoint, Instant now) {
        if (memory == null)
            return MIN_IMPORTANCE;
        if (memory.core() || !MemoryRepository.EVENT_CATEGORY.equals(memory.category()))
            return memory.importance();

        return effective(memory.importance(), memory.eventDate(), memory.createdAt(), daysPerPoint, now);
    }

    /**
     * Действующая важность: базовая минус прошедшие дни, делённые на скорость.
     * Деление целочисленное: балл снимается целиком, дробных важностей нет.
     */
    static int effective(int base, LocalDate eventDate, Instant createdAt, int daysPerPoint, Instant now) {
        if (daysPerPoint <= 0)
            return base;

        Instant anchor = eventDate == null ? createdAt : eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (anchor == null)
            return base;

        long days = Math.max(0, Duration.between(anchor, now).toDays());
        int lost = (int) (days / daysPerPoint);
        return Math.max(MIN_IMPORTANCE, base - lost);
    }
}
