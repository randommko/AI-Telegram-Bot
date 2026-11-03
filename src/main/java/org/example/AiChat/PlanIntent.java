package org.example.AiChat;

import java.util.Locale;
import java.util.Optional;

/** Намерение собеседника, которое план ответа распознал в последнем сообщении. */
public enum PlanIntent {
    QUESTION,
    REMARK,
    INSULT,
    APOLOGY,
    IGNORE;

    public static Optional<PlanIntent> from(String value) {
        if (value == null || value.isBlank())
            return Optional.empty();

        String code = value.trim().toLowerCase(Locale.ROOT);
        for (PlanIntent intent : values()) {
            if (intent.code().equals(code))
                return Optional.of(intent);
        }
        return Optional.empty();
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
