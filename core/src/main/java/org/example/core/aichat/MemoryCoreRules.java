package org.example.core.aichat;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ядро персоны защищено от автоматического анализа: удалить его может только
 * человек, обновить — можно. Создавать ядровые записи анализ тоже не умеет.
 */
public final class MemoryCoreRules {

    private MemoryCoreRules() {
    }

    public static List<Long> filterDeletable(Collection<Long> requested, Set<Long> coreIds) {
        return requested.stream().filter(id -> !coreIds.contains(id)).toList();
    }

    public static List<Long> protectedDeletes(Collection<Long> requested, Set<Long> coreIds) {
        return requested.stream().filter(coreIds::contains).toList();
    }

    public static Set<Long> ids(List<MemoryRecord> memories) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MemoryRecord memory : memories)
            ids.add((long) memory.id());
        return ids;
    }
}
