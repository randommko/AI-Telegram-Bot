package org.example.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Бот не должен видеть веб-слой админки. */
class ModuleBoundaryTest {

    @Test
    void botClasspathHasNoAdminCode() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.example.admin.AdminController"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.example.admin.AdminSecurityConfig"));
    }
}
