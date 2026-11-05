package org.example.core.aichat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Воспоминание персоны в том виде, в каком оно читается из базы. */
public record MemoryRecord(int id, String category, String content, int importance,
                           boolean core, LocalDate eventDate, List<String> people,
                           String source, Instant updatedAt) {

    public MemoryRecord(int id, String category, String content, int importance,
                        boolean core, LocalDate eventDate, List<String> people) {
        this(id, category, content, importance, core, eventDate, people, "manual", Instant.EPOCH);
    }
}
