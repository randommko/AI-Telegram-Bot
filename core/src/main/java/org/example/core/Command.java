package org.example.core;

import java.util.List;

/** Команды бота: одно место для имён и проверки алиасов персоны в админке. */
public enum Command {
    BOT_INFO("/bot_info", "/help"),
    AI("/ai", "/ии", "/аи"),
    AI_CLEAR_HISTORY("/clear");

    private final List<String> aliases;

    Command(String... aliases) {
        this.aliases = List.of(aliases);
    }

    /** Все написания команды — нужны проверке алиасов персоны в админке. */
    public List<String> aliases() {
        return aliases;
    }

    public static Command fromString(String cmd) {
        String cleanCmd = cmd.toLowerCase().trim();
        for (Command command : values()) {
            if (command.aliases.stream().anyMatch(alias ->
                    cleanCmd.startsWith(alias.toLowerCase()) ||
                            cleanCmd.equals(alias.replace("/", "").toLowerCase()))) {
                return command;
            }
        }
        return null;
    }
}
