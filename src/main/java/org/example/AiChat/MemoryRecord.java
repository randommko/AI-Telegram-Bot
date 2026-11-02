package org.example.AiChat;

import java.time.LocalDate;
import java.util.List;

/** Воспоминание персоны в том виде, в каком оно читается из базы. */
public record MemoryRecord(int id, String category, String content, int importance,
                           boolean core, LocalDate eventDate, List<String> people) {
}
