package org.example.Config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BotPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsValidConfiguration() {
        runner.withPropertyValues(validProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BotProperties.class).db().url())
                            .isEqualTo("jdbc:postgresql://db-host:5432/telegram_bot_db");
                });
    }

    @Test
    void rejectsBlankToken() {
        String[] properties = validProperties();
        properties[0] = "bot.token=";

        runner.withPropertyValues(properties)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsProxyPortOutOfRange() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.proxy.host=127.0.0.1", "bot.proxy.port=70000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsEmptyProxyPortWhenHostIsAbsent() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.proxy.port=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BotProperties.class).hasProxy()).isFalse();
                });
    }

    private String[] validProperties() {
        return new String[]{
                "bot.token=123:token",
                "bot.db.url=jdbc:postgresql://db-host:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key=sk-test"
        };
    }

    @Configuration
    @EnableConfigurationProperties(BotProperties.class)
    static class PropertiesConfig {
    }
}
