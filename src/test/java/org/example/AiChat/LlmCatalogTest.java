package org.example.AiChat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCatalogTest {

    @Test
    void hasFiveProvidersWithEnoughDataToCallThem() {
        assertThat(LlmCatalog.providers())
                .extracting(LlmCatalog.Provider::id)
                .containsExactly("deepseek", "grok", "openai", "gemini", "mistral");

        for (LlmCatalog.Provider provider : LlmCatalog.providers()) {
            assertThat(provider.title()).isNotBlank();
            assertThat(provider.baseUrl()).startsWith("https://");
            assertThat(provider.envName()).startsWith("BOT_AI_KEY_");
            assertThat(provider.timeout()).isPositive();
        }
    }

    @Test
    void providerIdsAndEnvNamesAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> envNames = new HashSet<>();

        for (LlmCatalog.Provider provider : LlmCatalog.providers()) {
            assertThat(ids.add(provider.id())).as("повтор идентификатора %s", provider.id()).isTrue();
            assertThat(envNames.add(provider.envName())).as("повтор переменной %s", provider.envName()).isTrue();
        }
    }

    @Test
    void everyModelBelongsToAKnownProviderAndHasUniqueId() {
        Set<String> ids = new HashSet<>();

        for (LlmCatalog.Model model : LlmCatalog.models()) {
            assertThat(ids.add(model.id())).as("повтор идентификатора модели %s", model.id()).isTrue();
            assertThat(model.title()).isNotBlank();
            assertThat(model.maxOutputTokens()).isPositive();
            assertThat(LlmCatalog.providerOf(model)).as("провайдер модели %s", model.id()).isPresent();
        }
    }

    @Test
    void everyProviderHasModels() {
        for (LlmCatalog.Provider provider : LlmCatalog.providers())
            assertThat(LlmCatalog.modelsOf(provider.id())).as("модели провайдера %s", provider.id()).isNotEmpty();
    }

    @Test
    void findsModelIgnoringSurroundingSpacesAndRejectsUnknown() {
        assertThat(LlmCatalog.model(" grok-4.7 ")).map(LlmCatalog.Model::providerId).contains("grok");
        assertThat(LlmCatalog.model("нет-такой-модели")).isEmpty();
        assertThat(LlmCatalog.model("")).isEmpty();
        assertThat(LlmCatalog.model(null)).isEmpty();
    }
}
