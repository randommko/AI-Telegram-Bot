package org.example.admin;

import org.example.aichat.MemoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Проверка карточки воспоминания: текст, категория, важность 1..10 и дата события. */
@Component
public class AdminMemoryValidator {
    static final int MIN_IMPORTANCE = 1;
    static final int MAX_IMPORTANCE = 10;
    static final String DEFAULT_CATEGORY = "другое";

    /** Либо готовая карточка, либо ошибки по полям формы. */
    public record Result(MemoryRepository.MemoryDetails details, Map<String, String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public Result validate(MemoryForm form, List<Long> people, Set<String> categories) {
        Map<String, String> errors = new LinkedHashMap<>();

        String content = form.content() == null ? "" : form.content().trim();
        if (content.isEmpty())
            errors.put("content", "Текст воспоминания не может быть пустым");

        String category = form.category() == null || form.category().isBlank()
                ? DEFAULT_CATEGORY : form.category().trim();
        if (!categories.contains(category))
            errors.put("category", "Неизвестная категория: " + category);

        int importance = importance(form.importance(), errors);
        LocalDate eventDate = eventDate(form.eventDate(), errors);

        if (form.personaId() == null)
            errors.put("personaId", "Не выбрана персона");

        if (errors.isEmpty()) {
            MemoryRepository.MemoryDetails details = new MemoryRepository.MemoryDetails(
                    form.id() == null ? 0 : form.id(),
                    form.personaId(),
                    category,
                    content,
                    importance,
                    Boolean.TRUE.equals(form.core()),
                    eventDate,
                    "manual",
                    people == null ? List.of() : people);
            return new Result(details, errors);
        }
        return new Result(null, errors);
    }

    private int importance(String raw, Map<String, String> errors) {
        try {
            int parsed = Integer.parseInt(raw == null ? "" : raw.trim());
            if (parsed < MIN_IMPORTANCE || parsed > MAX_IMPORTANCE) {
                errors.put("importance", "Важность: допустимо от " + MIN_IMPORTANCE + " до " + MAX_IMPORTANCE);
                return MIN_IMPORTANCE;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put("importance", "Важность: нужно целое число от " + MIN_IMPORTANCE + " до " + MAX_IMPORTANCE);
            return MIN_IMPORTANCE;
        }
    }

    private LocalDate eventDate(String raw, Map<String, String> errors) {
        if (raw == null || raw.isBlank())
            return null;

        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            errors.put("eventDate", "Дата события должна быть в формате ГГГГ-ММ-ДД или пустой");
            return null;
        }
    }
}
