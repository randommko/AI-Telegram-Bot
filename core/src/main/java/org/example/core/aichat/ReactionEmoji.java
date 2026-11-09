package org.example.core.aichat;

/**
 * Строка каталога эмодзи: сам эмодзи, текст «когда ставим» и два признака.
 * {@code memory} помечает метку памяти, {@code active} — участвует ли эмодзи в проходе.
 */
public record ReactionEmoji(String emoji, String meaning, boolean memory, boolean active) {

    public ReactionEmoji {
        meaning = meaning == null ? "" : meaning.trim();
    }

    /** Эмодзи можно предлагать модели: включён, не метка памяти и с текстом. */
    public boolean usableForEmotion() {
        return active && !memory && !meaning.isEmpty();
    }
}
