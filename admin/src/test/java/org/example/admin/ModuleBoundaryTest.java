package org.example.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Админка не должна видеть код бота: иначе разделение модулей ничего не даёт. */
class ModuleBoundaryTest {

    @Test
    void adminClasspathHasNoBotCode() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.example.bot.aichat.LlmClient"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.example.bot.CommandDispatcher"));
    }
}
