package org.example.Admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Без пароля админских страниц не должно существовать вовсе — иначе 404 превращается в форму входа. */
class AdminPagesConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AdminController.class);

    @Test
    void hasNoPagesWithoutPassword() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AdminController.class);
        });
    }

    @Test
    void treatsBlankPasswordAsDisabled() {
        runner.withPropertyValues("bot.admin.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AdminController.class);
                });
    }

    @Test
    void hasPagesWithPassword() {
        runner.withPropertyValues("bot.admin.password=secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdminController.class);
                });
    }
}
