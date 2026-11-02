package org.example;

import java.util.Optional;

/**
 * Данные кнопок подтверждения сброса памяти AI.
 * Автор команды и число забытых сообщений лежат в callback_data: состояние в памяти
 * процесса не пережило бы рестарт, а по callback_data видно, кто нажал.
 */
public record AiClearCallback(Decision decision, Long authorId, int forgottenCount) {

    private static final String PREFIX = "aiClear";
    private static final String SEPARATOR = ":";

    public enum Decision {
        CONFIRM("yes"),
        CANCEL("no");

        private final String code;

        Decision(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        static Decision fromCode(String code) {
            for (Decision decision : values()) {
                if (decision.code.equals(code))
                    return decision;
            }
            return null;
        }
    }

    public static Optional<AiClearCallback> parse(String callbackData) {
        if (callbackData == null)
            return Optional.empty();

        String[] parts = callbackData.split(SEPARATOR);
        if (parts.length != 4 || !PREFIX.equals(parts[0]))
            return Optional.empty();

        Decision decision = Decision.fromCode(parts[1]);
        if (decision == null)
            return Optional.empty();

        try {
            return Optional.of(new AiClearCallback(
                    decision,
                    Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public String toCallbackData() {
        return String.join(SEPARATOR, PREFIX, decision.code(), authorId.toString(), Integer.toString(forgottenCount));
    }
}
