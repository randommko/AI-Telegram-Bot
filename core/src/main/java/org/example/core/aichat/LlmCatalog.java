package org.example.core.aichat;

import java.math.BigDecimal;
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
     * по нему строится список групп в админке; потолок — больше модель не выдаст;
     * цены входа и выхода за миллион токенов в рублях необязательны и есть только там,
     * где площадка их публикует.
     */
    public record Model(String id, String providerId, String group, String title,
                        boolean jsonMode, int maxOutputTokens,
                        BigDecimal inputPricePerMillion, BigDecimal outputPricePerMillion) {

        public Model {
            group = group == null || group.isBlank() ? providerId : group;
        }

        /** Модель без цены: вендор совпадает с провайдером либо задан явно. */
        public Model(String id, String providerId, String group, String title,
                     boolean jsonMode, int maxOutputTokens) {
            this(id, providerId, group, title, jsonMode, maxOutputTokens, null, null);
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
            Map.entry("abliterated", "Abliterated"),
            Map.entry("minimax", "MiniMax"),
            Map.entry("gigachat", "GigaChat"),
            Map.entry("xiaomi", "Xiaomi MiMo"),
            Map.entry("hunyuan", "Tencent Hunyuan"),
            Map.entry("seed", "ByteDance Seed"));

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
                    "BOT_AI_KEY_ABLITERATION", Duration.ofSeconds(90), Kind.AGGREGATOR, Map.of(), "max_tokens"),
            // Рассуждающие флагманы polza отвечают дольше прямых провайдеров, поэтому таймаут как у AITunnel.
            new Provider("polza", "polza.ai", "https://polza.ai/api/v1/chat/completions",
                    "BOT_AI_KEY_POLZA", Duration.ofSeconds(120), Kind.AGGREGATOR, Map.of(), "max_tokens"));

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
            new Model("abliterated-model-large-flash", "abliteration", "abliterated", "Abliterated Large Flash", true, 8192),

            // polza: свежие поколения девяти знакомых семейств и пять новых вендоров.
            // Потолки, JSON-режим и цены — из каталога polza на 2026-11-10; у GigaChat
            // потолок вывода не публикуется, поэтому он равен максимуму настройки бота.
            new Model("deepseek/deepseek-v4.1-flash", "polza", "deepseek", "DeepSeek V4.1 Flash", true, 943718,
                    new BigDecimal("2.62"), new BigDecimal("47.69")),
            new Model("deepseek/deepseek-v4-pro", "polza", "deepseek", "DeepSeek V4 Pro", true, 393216,
                    new BigDecimal("107.30"), new BigDecimal("178.83")),
            new Model("deepseek/deepseek-v4-flash", "polza", "deepseek", "DeepSeek V4 Flash", true, 131072,
                    new BigDecimal("5.96"), new BigDecimal("11.92")),
            new Model("openai/gpt-6.1-terra", "polza", "openai", "GPT-6.1 Terra", true, 128000,
                    new BigDecimal("59.61"), new BigDecimal("298.04")),
            new Model("openai/gpt-6.1-sol", "polza", "openai", "GPT-6.1 Sol", true, 128000,
                    new BigDecimal("121.70"), new BigDecimal("608.51")),
            new Model("openai/gpt-5.6-luna", "polza", "openai", "GPT-5.6 Luna", true, 128000,
                    new BigDecimal("12.17"), new BigDecimal("73.02")),
            new Model("anthropic/claude-haiku-5.5", "polza", "anthropic", "Claude Haiku 5.5", true, 128000,
                    new BigDecimal("12.17"), new BigDecimal("60.85")),
            new Model("anthropic/claude-sonnet-5", "polza", "anthropic", "Claude Sonnet 5", true, 128000,
                    new BigDecimal("243.40"), new BigDecimal("1217.01")),
            new Model("anthropic/claude-opus-4.8", "polza", "anthropic", "Claude Opus 4.8", true, 128000,
                    new BigDecimal("608.51"), new BigDecimal("3042.53")),
            new Model("google/gemini-4-argon", "polza", "gemini", "Gemini 4 Argon", true, 65536,
                    new BigDecimal("120.05"), new BigDecimal("600.25")),
            new Model("google/gemini-4-carbon", "polza", "gemini", "Gemini 4 Carbon", true, 943718,
                    new BigDecimal("238.44"), new BigDecimal("1192.18")),
            new Model("google/gemini-3.8-flash", "polza", "gemini", "Gemini 3.8 Flash", true, 65536,
                    new BigDecimal("45.02"), new BigDecimal("225.09")),
            new Model("x-ai/grok-4.7", "polza", "grok", "Grok 4.7", true, 450000,
                    new BigDecimal("238.44"), new BigDecimal("715.31")),
            new Model("x-ai/grok-4.3", "polza", "grok", "Grok 4.3", true, 900000,
                    new BigDecimal("149.02"), new BigDecimal("298.04")),
            new Model("mistralai/mistral-large-2512", "polza", "mistral", "Mistral Large 3", true, 209715,
                    new BigDecimal("59.61"), new BigDecimal("178.83")),
            new Model("mistralai/mistral-small-2603", "polza", "mistral", "Mistral Small 4", true, 209715,
                    new BigDecimal("17.88"), new BigDecimal("71.53")),
            new Model("qwen/qwen3.8-max", "polza", "qwen", "Qwen 3.8 Max", true, 131072,
                    new BigDecimal("238.44"), new BigDecimal("715.31")),
            new Model("qwen/qwen3.8-flash", "polza", "qwen", "Qwen 3.8 Flash", true, 131072,
                    new BigDecimal("17.88"), new BigDecimal("56.03")),
            new Model("qwen/qwen3.6-plus", "polza", "qwen", "Qwen 3.6 Plus", true, 65536,
                    new BigDecimal("38.75"), new BigDecimal("232.47")),
            new Model("moonshotai/kimi-k3.1-pro", "polza", "kimi", "Kimi K3.1 Pro", true, 943718,
                    new BigDecimal("31.71"), new BigDecimal("991.75")),
            new Model("moonshotai/kimi-k2.6", "polza", "kimi", "Kimi K2.6", true, 235929,
                    new BigDecimal("46.44"), new BigDecimal("195.52")),
            new Model("z-ai/glm-5.3", "polza", "glm", "GLM 5.3", true, 943718,
                    new BigDecimal("8.33"), new BigDecimal("166.90")),
            new Model("z-ai/glm-5.3-flashx", "polza", "glm", "GLM 5.3 FlashX", true, 131072,
                    new BigDecimal("44.11"), new BigDecimal("149.02")),
            new Model("z-ai/glm-5.2", "polza", "glm", "GLM 5.2", true, 163840,
                    new BigDecimal("53.65"), new BigDecimal("171.67")),
            new Model("z-ai/glm-5.3-flash", "polza", "glm", "GLM 5.3 Flash", false, 131072,
                    new BigDecimal("4.76"), new BigDecimal("9.35")),
            new Model("minimax/minimax-m3", "polza", "minimax", "MiniMax M3", true, 131072,
                    new BigDecimal("26.70"), new BigDecimal("104.91")),
            new Model("minimax/minimax-m3.1-flash", "polza", "minimax", "MiniMax M3.1 Flash", true, 524288,
                    new BigDecimal("8.94"), new BigDecimal("35.77")),
            new Model("GigaChat/GigaChat-3-Pro", "polza", "gigachat", "GigaChat 3 Pro", true, 8192,
                    new BigDecimal("105.16"), new BigDecimal("254.00")),
            new Model("sber/gigachat-2", "polza", "gigachat", "GigaChat 2", false, 8192,
                    new BigDecimal("93.60"), new BigDecimal("93.60")),
            new Model("xiaomi/mimo-v2.6-flash", "polza", "xiaomi", "MiMo V2.6 Flash", true, 945000,
                    new BigDecimal("13.35"), new BigDecimal("26.70")),
            new Model("xiaomi/mimo-v2.6-pro", "polza", "xiaomi", "MiMo V2.6 Pro", true, 945000,
                    new BigDecimal("38.89"), new BigDecimal("77.79")),
            new Model("tencent/hy3", "polza", "hunyuan", "Hunyuan Hy3", true, 128000,
                    new BigDecimal("9.84"), new BigDecimal("39.34")),
            new Model("tencent/hy4-preview", "polza", "hunyuan", "Hunyuan Hy4 Preview", true, 64000,
                    new BigDecimal("89.48"), new BigDecimal("268.35")),
            new Model("bytedance-seed/seed-2.0-lite", "polza", "seed", "Seed 2.0 Lite", true, 131072,
                    new BigDecimal("29.80"), new BigDecimal("238.44")),
            new Model("bytedance-seed/seed-2.0-mini", "polza", "seed", "Seed 2.0 Mini", true, 131072,
                    new BigDecimal("11.92"), new BigDecimal("47.69")),
            new Model("bytedance-seed/seed-1.6-flash", "polza", "seed", "Seed 1.6 Flash", true, 32768,
                    new BigDecimal("8.94"), new BigDecimal("35.77")));

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
