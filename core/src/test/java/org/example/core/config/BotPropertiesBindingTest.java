package org.example.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void startsWithoutTokenBecauseAdminHasNone() {
        String[] properties = validProperties();
        properties[0] = "bot.token=";

        runner.withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getBean(BotProperties.class).botId())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("BOT_TOKEN");
                });
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

    @Test
    void startsWithoutAdminSettings() {
        runner.withPropertyValues(validProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BotProperties.class).hasAdmin()).isFalse();
                });
    }

    @Test
    void bindsAdminSettings() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.admin.password=secret", "bot.admin.port=8081",
                        "bot.admin.bind=127.0.0.1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BotProperties properties = context.getBean(BotProperties.class);
                    assertThat(properties.hasAdmin()).isTrue();
                    assertThat(properties.admin().port()).isEqualTo(8081);
                });
    }

    @Test
    void treatsBlankAdminPasswordAsDisabled() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.admin.password=")
                .run(context -> assertThat(context.getBean(BotProperties.class).hasAdmin()).isFalse());
    }

    @Test
    void rejectsAdminPortOutOfRange() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.admin.password=secret", "bot.admin.port=70000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsProviderKeysById() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("bot.ai.key.grok=sk-grok", "bot.ai.key.openai=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BotProperties.Ai ai = context.getBean(BotProperties.class).ai();
                    assertThat(ai.keyFor("deepseek")).isEqualTo("sk-test");
                    assertThat(ai.keyFor("grok")).isEqualTo("sk-grok");
                    assertThat(ai.keyFor("openai")).isNull();
                    assertThat(ai.keyFor("mistral")).isNull();
                });
    }

    @Test
    void startsWithoutAnyProviderKey() {
        String[] properties = validProperties();
        properties[4] = "bot.ai.key.deepseek=";

        runner.withPropertyValues(properties)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BotProperties.class).ai().keyFor("deepseek")).isNull();
                });
    }

    private String[] validProperties() {
        return new String[]{
                "bot.token=123:token",
                "bot.db.url=jdbc:postgresql://db-host:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        };
    }

    @Configuration
    @EnableConfigurationProperties(BotProperties.class)
    static class PropertiesConfig {
    }
}
