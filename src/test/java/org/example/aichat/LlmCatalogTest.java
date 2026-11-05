package org.example.aichat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCatalogTest {

    @Test
    void hasFiveDirectProvidersAndThreeAggregatorsWithEnoughDataToCallThem() {
        assertThat(LlmCatalog.directProviders())
                .extracting(LlmCatalog.Provider::id)
                .containsExactly("deepseek", "grok", "openai", "gemini", "mistral");
        assertThat(LlmCatalog.aggregators())
                .extracting(LlmCatalog.Provider::id)
                .containsExactly("aitunnel", "venice", "abliteration");

        for (LlmCatalog.Provider provider : LlmCatalog.providers()) {
            assertThat(provider.title()).isNotBlank();
            assertThat(provider.baseUrl()).startsWith("https://");
            assertThat(provider.envName()).startsWith("BOT_AI_KEY_");
            assertThat(provider.timeout()).isPositive();
            assertThat(provider.maxTokensField()).isNotBlank();
        }
    }

    @Test
    void directProviderSendsNoExtraBodyAndKeepsMaxTokensField() {
        for (LlmCatalog.Provider provider : LlmCatalog.directProviders()) {
            assertThat(provider.kind()).isEqualTo(LlmCatalog.Kind.DIRECT);
            assertThat(provider.extraBody()).isEmpty();
            assertThat(provider.maxTokensField()).isEqualTo("max_tokens");
        }
    }

    @Test
    void veniceDisablesItsOwnSystemPromptAndRenamesTokenField() {
        LlmCatalog.Provider venice = LlmCatalog.provider("venice").orElseThrow();

        assertThat(venice.extraBody()).containsKey("venice_parameters");
        assertThat(venice.maxTokensField()).isEqualTo("max_completion_tokens");
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
    void everyModelBelongsToAKnownProviderAndIsUniqueInsideIt() {
        Set<String> pairs = new HashSet<>();

        for (LlmCatalog.Model model : LlmCatalog.models()) {
            String pair = model.providerId() + "/" + model.id();
            assertThat(pairs.add(pair)).as("повтор пары %s", pair).isTrue();
            assertThat(model.title()).isNotBlank();
            assertThat(model.maxOutputTokens()).isPositive();
            assertThat(LlmCatalog.providerOf(model)).as("провайдер модели %s", model.id()).isPresent();
        }
    }

    @Test
    void directModelBelongsToItsOwnVendorGroup() {
        for (LlmCatalog.Provider provider : LlmCatalog.directProviders()) {
            for (LlmCatalog.Model model : LlmCatalog.modelsOf(provider.id()))
                assertThat(model.group()).as("вендор модели %s", model.id()).isEqualTo(provider.id());
        }
    }

    @Test
    void everyProviderHasModelsAndAtLeastOneGroup() {
        for (LlmCatalog.Provider provider : LlmCatalog.providers()) {
            assertThat(LlmCatalog.modelsOf(provider.id())).as("модели провайдера %s", provider.id()).isNotEmpty();
            assertThat(LlmCatalog.groupsOf(provider.id())).as("группы провайдера %s", provider.id()).isNotEmpty();
        }
    }

    @Test
    void aggregatorSplitsModelsIntoVendorGroups() {
        assertThat(LlmCatalog.groupsOf("aitunnel"))
                .extracting(LlmCatalog.Group::title)
                .containsExactly("DeepSeek", "Grok", "OpenAI", "Google Gemini", "Mistral");
        assertThat(LlmCatalog.groupsOf("abliteration"))
                .extracting(LlmCatalog.Group::title)
                .containsExactly("Abliterated");
    }

    @Test
    void sameModelExistsUnderSeveralProviders() {
        List<LlmCatalog.Model> doors = LlmCatalog.models("gemini-4-argon");

        assertThat(doors).extracting(LlmCatalog.Model::providerId)
                .containsExactlyInAnyOrder("gemini", "aitunnel", "venice");
        assertThat(LlmCatalog.model("aitunnel", "gemini-4-argon"))
                .map(LlmCatalog.Model::providerId).contains("aitunnel");
    }

    @Test
    void vendorStaysTheSameWhenAggregatorSpellsModelDifferently() {
        assertThat(LlmCatalog.model("venice", "grok-4-7")).map(LlmCatalog.Model::group).contains("grok");
        assertThat(LlmCatalog.model("grok", "grok-4.7")).map(LlmCatalog.Model::group).contains("grok");
    }

    @Test
    void lookupWithoutDoorPrefersDirectProvider() {
        assertThat(LlmCatalog.model(" gemini-4-argon ")).map(LlmCatalog.Model::providerId).contains("gemini");
        assertThat(LlmCatalog.model("grok-4.7")).map(LlmCatalog.Model::providerId).contains("grok");
        assertThat(LlmCatalog.model("abliterated-model")).map(LlmCatalog.Model::providerId)
                .contains("abliteration");
    }

    @Test
    void unknownDoorOrModelGivesEmpty() {
        assertThat(LlmCatalog.model("grok", "gemini-4-argon")).isEmpty();
        assertThat(LlmCatalog.model("aitunnel", "deepseek-flash")).isEmpty();
        assertThat(LlmCatalog.model("нет-такой-модели")).isEmpty();
        assertThat(LlmCatalog.model("")).isEmpty();
        assertThat(LlmCatalog.model((String) null)).isEmpty();
        assertThat(LlmCatalog.model("grok", " ")).isEmpty();
    }
}
