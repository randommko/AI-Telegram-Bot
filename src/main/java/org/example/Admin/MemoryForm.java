package org.example.Admin;

import org.example.AiChat.MemoryRepository;

/** Поля карточки воспоминания как строки — чтобы ошибка появлялась у конкретного поля. */
public record MemoryForm(
        Integer id,
        Integer personaId,
        String category,
        String content,
        String importance,
        String eventDate,
        Boolean core,
        String source) {

    public static MemoryForm blank(int personaId, String category) {
        return new MemoryForm(null, personaId, category, "", "5", "", false, "manual");
    }

    public static MemoryForm of(MemoryRepository.MemoryDetails details) {
        return new MemoryForm(
                details.id(),
                details.personaId(),
                details.category(),
                details.content(),
                String.valueOf(details.importance()),
                details.eventDate() == null ? "" : details.eventDate().toString(),
                details.core(),
                details.source());
    }
}
