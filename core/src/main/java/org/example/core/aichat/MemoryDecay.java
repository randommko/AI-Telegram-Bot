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
import static org.example.core.settings.Settings.AI_MEMORY_EVENT_MIN_IMPORTANCE;

/**
 * Затухание важности событий: как у эмоций, значение считается при чтении,
 * планировщика нет. Друзья, семья, хобби и работа — факты, они не стареют.
 */
@Component
public class MemoryDecay {
    private static final Logger logger = LoggerFactory.getLogger(MemoryDecay.class);
    static final int DEFAULT_DAYS_PER_POINT = 3;
    static final int MAX_DAYS_PER_POINT = 365;
    /** Ниже этого значения важность не опускается никогда: шкала начинается с единицы. */
    static final int MIN_IMPORTANCE = 1;
    static final int MAX_IMPORTANCE = 10;

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

    /** Порог, ниже которого событие не стареет; 1 — прежнее поведение. */
    public int minImportance() {
        String configured = settings.getSettingValue(AI_MEMORY_EVENT_MIN_IMPORTANCE);
        if (configured == null || configured.isBlank())
            return MIN_IMPORTANCE;

        try {
            int min = Integer.parseInt(configured.trim());
            if (min >= MIN_IMPORTANCE && min <= MAX_IMPORTANCE)
                return min;
            logger.warn("Порог важности {} вне диапазона {}..{}: используется {}",
                    min, MIN_IMPORTANCE, MAX_IMPORTANCE, MIN_IMPORTANCE);
        } catch (NumberFormatException e) {
            logger.warn("Некорректный порог важности '{}': используется {}", configured, MIN_IMPORTANCE);
        }
        return MIN_IMPORTANCE;
    }

    public int effective(MemoryRecord memory) {
        return effective(memory, daysPerPoint(), minImportance(), clock.instant());
    }

    /** Стареют только события: ядро и остальные типы — факты, их важность не меняется. */
    public int effective(MemoryRecord memory, int daysPerPoint, int minImportance, Instant now) {
        if (memory == null)
            return MIN_IMPORTANCE;
        if (memory.core() || !MemoryRepository.EVENT_CATEGORY.equals(memory.category()))
            return memory.importance();

        return effective(memory.importance(), memory.eventDate(), memory.createdAt(),
                daysPerPoint, minImportance, now);
    }

    /**
     * Действующая важность: базовая минус прошедшие дни, делённые на скорость.
     * Деление целочисленное: балл снимается целиком, дробных важностей нет.
     * Порог не может поднять важность выше базовой.
     */
    static int effective(int base, LocalDate eventDate, Instant createdAt, int daysPerPoint,
                         int minImportance, Instant now) {
        if (daysPerPoint <= 0)
            return base;

        Instant anchor = eventDate == null ? createdAt : eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (anchor == null)
            return base;

        long days = Math.max(0, Duration.between(anchor, now).toDays());
        int lost = (int) (days / daysPerPoint);
        int floor = Math.min(Math.max(MIN_IMPORTANCE, minImportance), base);
        return Math.max(floor, base - lost);
    }
}
