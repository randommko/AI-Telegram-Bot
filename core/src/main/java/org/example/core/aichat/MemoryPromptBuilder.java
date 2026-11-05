package org.example.core.aichat;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Собирает блок памяти для системного промпта: ядро первым и целиком,
 * затем обычные воспоминания. Даты и люди добавляются, только если заполнены.
 */
@Component
public class MemoryPromptBuilder {

    public String build(List<MemoryRecord> core, List<MemoryRecord> regular) {
        return build(core, regular, PromptDefaults.MEMORY_CORE_HEADER, PromptDefaults.MEMORY_REGULAR_HEADER);
    }

    /** Заголовки приходят из настроек: смена текста в админке действует без перезапуска. */
    public String build(List<MemoryRecord> core, List<MemoryRecord> regular,
                        String coreHeader, String regularHeader) {
        List<String> blocks = new ArrayList<>();
        if (core != null && !core.isEmpty())
            blocks.add(block(header(coreHeader, PromptDefaults.MEMORY_CORE_HEADER), core));
        if (regular != null && !regular.isEmpty())
            blocks.add(block(header(regularHeader, PromptDefaults.MEMORY_REGULAR_HEADER), regular));
        return String.join("\n\n", blocks);
    }

    private String header(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private String block(String header, List<MemoryRecord> memories) {
        return header + "\n" + memories.stream().map(this::line).collect(Collectors.joining("\n"));
    }

    private String line(MemoryRecord memory) {
        List<String> details = new ArrayList<>();
        details.add("важно: " + memory.importance());
        if (memory.eventDate() != null)
            details.add(memory.eventDate().toString());
        if (memory.people() != null && !memory.people().isEmpty())
            details.add(memory.people().stream().map(person -> "@" + person).collect(Collectors.joining(", ")));

        return "- [" + memory.category() + "] " + memory.content() + " (" + String.join(", ", details) + ")";
    }
}
