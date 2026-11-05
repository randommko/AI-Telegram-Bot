package org.example.core.aichat;

import java.time.LocalDate;
import java.util.List;

/** Одна операция из ответа модели: id есть только у обновления. */
public record MemoryDraft(Long id, String category, String content, int importance,
                          LocalDate eventDate, List<String> people) {
}
