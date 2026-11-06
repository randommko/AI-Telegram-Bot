package org.example.core.aichat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Воспоминание персоны в том виде, в каком оно читается из базы.
 * {@code createdAt} — точка отсчёта для затухания важности, когда у события нет даты.
 */
public record MemoryRecord(int id, String category, String content, int importance,
                           boolean core, LocalDate eventDate, List<String> people,
                           String source, Instant updatedAt, Instant createdAt) {

    public MemoryRecord(int id, String category, String content, int importance,
                        boolean core, LocalDate eventDate, List<String> people,
                        String source, Instant updatedAt) {
        this(id, category, content, importance, core, eventDate, people, source, updatedAt, updatedAt);
    }

    public MemoryRecord(int id, String category, String content, int importance,
                        boolean core, LocalDate eventDate, List<String> people) {
        this(id, category, content, importance, core, eventDate, people, "manual", Instant.EPOCH);
    }
}
