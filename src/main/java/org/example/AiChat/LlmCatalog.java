package org.example.AiChat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Единственное место, где перечислены провайдеры LLM, их адреса, переменные окружения
 * с ключами и потолки выходных токенов моделей. Список меняется вместе с кодом:
 * новый провайдер без деплоя всё равно недоступен, потому что ключ приходит из окружения.
 */
public final class LlmCatalog {

    /** Прямой адрес поставщика или агрегатор, который сам ходит к поставщикам. */
    public enum Kind { DIRECT, AGGREGATOR }

    /**
     * Провайдер говорит на формате Chat Completions от OpenAI: отличается адресом, ключом и,
     * у некоторых площадок, дополнительными полями тела запроса и именем поля лимита токенов.
     */
    public record Provider(String id, String title, String baseUrl, String envName, Duration timeout,
                           Kind kind, Map<String, Object> extraBody, String maxTokensField) {

        public Provider {
            extraBody = extraBody == null ? Map.of() : Map.copyOf(extraBody);
            maxTokensField = maxTokensField == null || maxTokensField.isBlank() ? "max_tokens" : maxTokensField;
        }

        /** Прямое подключение: тело запроса не дополняется, лимит уходит как max_tokens. */
        public Provider(String id, String title, String baseUrl, String envName, Duration timeout) {
            this(id, title, baseUrl, envName, timeout, Kind.DIRECT, Map.of(), "max_tokens");
        }

        public boolean aggregator() {
            return kind == Kind.AGGREGATOR;
        }
    }

    /**
     * jsonMode — провайдер принимает response_format: json_object; group — вендор модели,
     * по нему строится список групп в админке; потолок — больше модель не выдаст.
     */
    public record Model(String id, String providerId, String group, String title,
                        boolean jsonMode, int maxOutputTokens) {

        public Model {
            group = group == null || group.isBlank() ? providerId : group;
        }

        /** Прямое подключение: вендор совпадает с провайдером. */
        public Model(String id, String providerId, String title, boolean jsonMode, int maxOutputTokens) {
            this(id, providerId, providerId, title, jsonMode, maxOutputTokens);
        }
    }

    /** Группа моделей внутри провайдера: идентификатор вендора и его подпись в админке. */
    public record Group(String id, String title) {
    }

    private static final Map<String, String> GROUP_TITLES = Map.ofEntries(
            Map.entry("deepseek", "DeepSeek"),
            Map.entry("grok", "Grok"),
            Map.entry("openai", "OpenAI"),
            Map.entry("gemini", "Google Gemini"),
            Map.entry("mistral", "Mistral"),
            Map.entry("anthropic", "Anthropic"),
            Map.entry("qwen", "Qwen"),
            Map.entry("kimi", "Kimi"),
            Map.entry("glm", "GLM"),
            Map.entry("abliterated", "Abliterated"));

    private static final List<Provider> PROVIDERS = List.of(
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1/chat/completions",
                    "BOT_AI_KEY_DEEPSEEK", Duration.ofSeconds(60)),
            new Provider("grok", "Grok", "https://api.x.ai/v1/chat/completions",
                    "BOT_AI_KEY_GROK", Duration.ofSeconds(90)),
            new Provider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions",
                    "BOT_AI_KEY_OPENAI", Duration.ofSeconds(90)),
            new Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    "BOT_AI_KEY_GEMINI", Duration.ofSeconds(90)),
            new Provider("mistral", "Mistral", "https://api.mistral.ai/v1/chat/completions",
                    "BOT_AI_KEY_MISTRAL", Duration.ofSeconds(60)),
            // Агрегатор сам ретраит 429 апстрима, поэтому таймаут выше прямых подключений.
            new Provider("aitunnel", "AITunnel", "https://api.aitunnel.ru/v1/chat/completions",
                    "BOT_AI_KEY_AITUNNEL", Duration.ofSeconds(120), Kind.AGGREGATOR, Map.of(), "max_tokens"),
            // Venice по умолчанию подмешивает свой системный промпт рядом с промптом персоны,
            // поэтому он отключается в каждом запросе; max_tokens у неё объявлен устаревшим.
            new Provider("venice", "Venice", "https://api.venice.ai/api/v1/chat/completions",
                    "BOT_AI_KEY_VENICE", Duration.ofSeconds(90), Kind.AGGREGATOR,
                    Map.of("venice_parameters", Map.of(
                            "include_venice_system_prompt", false,
                            "strip_thinking_response", true)),
                    "max_completion_tokens"),
            new Provider("abliteration", "Abliteration", "https://api.abliteration.ai/v1/chat/completions",
                    "BOT_AI_KEY_ABLITERATION", Duration.ofSeconds(90), Kind.AGGREGATOR, Map.of(), "max_tokens"));

    private static final List<Model> MODELS = List.of(
            new Model("deepseek-flash", "deepseek", "flash, рассуждения по умолчанию", true, 8192),
            new Model("deepseek-v4-pro", "deepseek", "v4 pro, рассуждения", true, 8192),
            new Model("grok-4.7", "grok", "Grok 4.7", true, 500000),
            new Model("grok-4.5", "grok", "Grok 4.5", true, 500000),
            new Model("grok-4.3", "grok", "Grok 4.3", true, 1000000),
            new Model("gpt-5.6", "openai", "GPT-5.6", true, 128000),
            new Model("gpt-5.2", "openai", "GPT-5.2", true, 128000),
            new Model("gpt-4o", "openai", "GPT-4o", true, 16384),
            // Режим совместимости Gemini не заявляет response_format: JSON-режим выключен.
            new Model("gemini-4-argon", "gemini", "Gemini 4 Argon", false, 65536),
            new Model("gemini-3.8-flash", "gemini", "Gemini 3.8 Flash", false, 32768),
            new Model("gemini-3.1-pro-preview", "gemini", "Gemini 3.1 Pro (preview)", false, 32768),
            new Model("mistral-large-4-0", "mistral", "Mistral Large 4", true, 8192),
            new Model("mistral-small-4-0-26-03", "mistral", "Mistral Small 4", true, 8192),
            new Model("ministral-3-14b-25-12", "mistral", "Ministral 3 14B", true, 8192),

            // AITunnel повторяет те же модели под своими идентификаторами.
            new Model("deepseek-v4-pro", "aitunnel", "deepseek", "deepseek-v4-pro", true, 384000),
            new Model("grok-4.7", "aitunnel", "grok", "Grok 4.7", true, 450000),
            new Model("grok-4.5", "aitunnel", "grok", "Grok 4.5", true, 256000),
            new Model("grok-4.3", "aitunnel", "grok", "Grok 4.3", true, 256000),
            new Model("gpt-5.2", "aitunnel", "openai", "GPT-5.2", true, 128000),
            new Model("gpt-4o", "aitunnel", "openai", "GPT-4o", true, 16384),
            new Model("gemini-4-argon", "aitunnel", "gemini", "Gemini 4 Argon", true, 65536),
            new Model("gemini-3.8-flash", "aitunnel", "gemini", "Gemini 3.8 Flash", true, 65536),
            new Model("gemini-3.1-pro-preview", "aitunnel", "gemini", "Gemini 3.1 Pro (preview)", true, 65536),
            new Model("mistral-large-4-0", "aitunnel", "mistral", "Mistral Large 4", true, 262144),

            // Venice: наши модели в её написании, плюс Claude, Qwen, Kimi и GLM.
            // Потолок выхода площадка не публикует, поэтому он равен максимуму настройки бота.
            new Model("deepseek-v4-pro", "venice", "deepseek", "deepseek-v4-pro", true, 8192),
            new Model("deepseek-v4-1-flash", "venice", "deepseek", "deepseek-v4.1-flash", true, 8192),
            new Model("grok-4-7", "venice", "grok", "Grok 4.7", true, 8192),
            new Model("grok-4-5", "venice", "grok", "Grok 4.5", true, 8192),
            new Model("grok-4-3", "venice", "grok", "Grok 4.3", true, 8192),
            new Model("openai-gpt-52", "venice", "openai", "GPT-5.2", true, 8192),
            new Model("openai-gpt-4o-2024-11-20", "venice", "openai", "GPT-4o (2024-11-20)", true, 8192),
            new Model("gemini-4-argon", "venice", "gemini", "Gemini 4 Argon", true, 8192),
            new Model("gemini-3-8-flash", "venice", "gemini", "Gemini 3.8 Flash", true, 8192),
            new Model("gemini-3-1-pro-preview", "venice", "gemini", "Gemini 3.1 Pro (preview)", true, 8192),
            new Model("claude-opus-5-5", "venice", "anthropic", "Claude Opus 5.5", true, 8192),
            new Model("claude-opus-5", "venice", "anthropic", "Claude Opus 5", true, 8192),
            new Model("claude-sonnet-5-5", "venice", "anthropic", "Claude Sonnet 5.5", true, 8192),
            new Model("claude-sonnet-5", "venice", "anthropic", "Claude Sonnet 5", true, 8192),
            new Model("claude-haiku-5-5", "venice", "anthropic", "Claude Haiku 5.5", true, 8192),
            new Model("claude-fable-5-1", "venice", "anthropic", "Claude Fable 5.1", true, 8192),
            new Model("claude-fable-5", "venice", "anthropic", "Claude Fable 5", true, 8192),
            new Model("qwen-3-8-max", "venice", "qwen", "Qwen 3.8 Max", true, 8192),
            new Model("qwen-3-8-flash", "venice", "qwen", "Qwen 3.8 Flash", true, 8192),
            new Model("qwen-3-7-max", "venice", "qwen", "Qwen 3.7 Max", true, 8192),
            new Model("qwen-3-6-plus", "venice", "qwen", "Qwen 3.6 Plus", true, 8192),
            new Model("qwen3-6-35b-a3b", "venice", "qwen", "Qwen 3.6 35B", true, 8192),
            new Model("qwen3-next-80b", "venice", "qwen", "Qwen 3 Next 80B", true, 8192),
            new Model("qwen-3-8-2-4t-a95b", "venice", "qwen", "Qwen 3.8 2.4T", true, 8192),
            new Model("kimi-k3", "venice", "kimi", "Kimi K3", true, 8192),
            new Model("kimi-k3-1-pro", "venice", "kimi", "Kimi K3.1 Pro", true, 8192),
            new Model("kimi-k3-fast-api", "venice", "kimi", "Kimi K3 (fast)", true, 8192),
            new Model("kimi-k2-6", "venice", "kimi", "Kimi K2.6", true, 8192),
            new Model("z-ai-glm-5-3", "venice", "glm", "GLM 5.3", true, 8192),
            new Model("z-ai-glm-5-3-flash", "venice", "glm", "GLM 5.3 Flash", true, 8192),
            new Model("z-ai-glm-5-turbo", "venice", "glm", "GLM 5 Turbo", true, 8192),
            new Model("zai-org-glm-5-2", "venice", "glm", "GLM 5.2", true, 8192),
            new Model("zai-org-glm-5-1", "venice", "glm", "GLM 5.1", true, 8192),
            new Model("zai-org-glm-5", "venice", "glm", "GLM 5", true, 8192),

            // Abliteration: четыре собственные модели, лимиты площадка не публикует.
            new Model("abliterated-model", "abliteration", "abliterated", "Abliterated Model", true, 8192),
            new Model("abliterated-model-large", "abliteration", "abliterated", "Abliterated Large", true, 8192),
            new Model("abliterated-model-large-v2", "abliteration", "abliterated", "Abliterated Large v2", true, 8192),
            new Model("abliterated-model-large-flash", "abliteration", "abliterated", "Abliterated Large Flash", true, 8192));

    private LlmCatalog() {
    }

    public static List<Provider> providers() {
        return PROVIDERS;
    }

    public static List<Provider> directProviders() {
        return PROVIDERS.stream().filter(provider -> provider.kind() == Kind.DIRECT).toList();
    }

    public static List<Provider> aggregators() {
        return PROVIDERS.stream().filter(Provider::aggregator).toList();
    }

    public static List<Model> models() {
        return MODELS;
    }

    /** Модель по одному идентификатору: так ищет настройка без двери, предпочитая прямой адрес. */
    public static Optional<Model> model(String id) {
        return model(null, id);
    }

    /** Модель по паре «провайдер и идентификатор»; пустой провайдер даёт прямую дверь. */
    public static Optional<Model> model(String providerId, String id) {
        if (id == null || id.isBlank())
            return Optional.empty();

        String trimmed = id.trim();
        if (providerId != null && !providerId.isBlank()) {
            String door = providerId.trim();
            return MODELS.stream()
                    .filter(model -> model.providerId().equals(door) && model.id().equals(trimmed))
                    .findFirst();
        }

        List<Model> found = models(trimmed);
        return found.stream()
                .filter(model -> isDirect(model.providerId()))
                .findFirst()
                .or(() -> found.stream().findFirst());
    }

    /** Все записи с этим идентификатором: одна и та же модель бывает у нескольких провайдеров. */
    public static List<Model> models(String id) {
        if (id == null || id.isBlank())
            return List.of();

        String trimmed = id.trim();
        return MODELS.stream().filter(model -> model.id().equals(trimmed)).toList();
    }

    public static Optional<Provider> provider(String id) {
        return PROVIDERS.stream().filter(provider -> provider.id().equals(id)).findFirst();
    }

    public static Optional<Provider> providerOf(Model model) {
        return provider(model.providerId());
    }

    public static List<Model> modelsOf(String providerId) {
        return MODELS.stream().filter(model -> model.providerId().equals(providerId)).toList();
    }

    /** Группы моделей провайдера в порядке каталога; провайдер без моделей даёт пустой список. */
    public static List<Group> groupsOf(String providerId) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Model model : modelsOf(providerId))
            groups.putIfAbsent(model.group(), group(model.group()));
        return List.copyOf(groups.values());
    }

    public static Group group(String id) {
        return new Group(id, GROUP_TITLES.getOrDefault(id, id));
    }

    private static boolean isDirect(String providerId) {
        return provider(providerId).map(provider -> provider.kind() == Kind.DIRECT).orElse(false);
    }
}
