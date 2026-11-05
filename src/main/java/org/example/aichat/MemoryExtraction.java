package org.example.aichat;

import java.util.List;

/** Разобранный ответ модели: что добавить, что обновить и что удалить. */
public record MemoryExtraction(List<MemoryDraft> add, List<MemoryDraft> update, List<Long> delete) {

    public int size() {
        return add.size() + update.size() + delete.size();
    }
}
