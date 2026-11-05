package org.example.core.aichat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryCoreRulesTest {

    @Test
    void coreRecordsAreNotDeletable() {
        assertEquals(List.of(6L, 8L), MemoryCoreRules.filterDeletable(List.of(5L, 6L, 7L, 8L), Set.of(5L, 7L)));
    }

    @Test
    void protectedDeletesAreReported() {
        assertEquals(List.of(5L, 7L), MemoryCoreRules.protectedDeletes(List.of(5L, 6L, 7L), Set.of(5L, 7L)));
    }

    @Test
    void idsAreCollectedFromRecords() {
        List<MemoryRecord> memories = List.of(
                new MemoryRecord(3, "семья", "есть брат", 9, true, null, List.of()),
                new MemoryRecord(4, "хобби", "шахматы", 5, false, null, List.of()));

        assertEquals(Set.of(3L, 4L), MemoryCoreRules.ids(memories));
    }
}
