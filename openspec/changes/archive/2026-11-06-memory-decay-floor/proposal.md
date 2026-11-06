# Proposal

## Why

Затухание уменьшает важность события только до единицы. При скорости один балл в день все события быстро упираются в этот пол, и порядок в пуле начинает задавать дата, а не важность: важное событие недельной давности неотличимо от случайного (задача #89).

## What Changes

- Появляется настройка «Минимальная важность события» (1..10, по умолчанию 1): действующая важность не опускается ниже порога.
- Порог не поднимает важность выше базовой: событие с важностью 2 при пороге 5 остаётся двойкой.
- Порог не влияет на факты и ядро — они и так не стареют.
- Строка настройки с описанием добавляется миграцией `migrations/config/004_memory_decay_floor.sql`.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `prompt-composition`: требование «Важность событий убывает со временем» получает настраиваемый порог вместо жёсткой единицы.

## Impact

- `core/src/main/java/org/example/core/aichat/MemoryDecay.java` — порог и формула.
- `core/src/main/java/org/example/core/aichat/PromptComposer.java` — передача порога в ранжирование пула.
- `core/src/main/java/org/example/core/settings/Settings.java`, `admin/.../AdminSettingsController.java` — настройка на вкладке «Память».
- Тесты `MemoryDecayTest`, `PromptComposerTest`.
