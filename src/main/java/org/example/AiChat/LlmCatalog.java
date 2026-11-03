package org.example.AiChat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Единственное место, где перечислены провайдеры LLM, их адреса, переменные окружения
 * с ключами и потолки выходных токенов моделей. Список меняется вместе с кодом:
 * новый провайдер без деплоя всё равно недоступен, потому что ключ приходит из окружения.
 */
public final class LlmCatalog {

    /** Провайдер говорит на формате Chat Completions от OpenAI: отличается только адресом и ключом. */
    public record Provider(String id, String title, String baseUrl, String envName, Duration timeout) {
    }

    /** jsonMode — провайдер принимает response_format: json_object; потолок — больше модель не выдаст. */
    public record Model(String id, String providerId, String title, boolean jsonMode, int maxOutputTokens) {
    }

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
                    "BOT_AI_KEY_MISTRAL", Duration.ofSeconds(60)));

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
            new Model("ministral-3-14b-25-12", "mistral", "Ministral 3 14B", true, 8192));

    private LlmCatalog() {
    }

    public static List<Provider> providers() {
        return PROVIDERS;
    }

    public static List<Model> models() {
        return MODELS;
    }

    /** Модель по идентификатору из настроек; пустое или непустое неизвестное значение даёт empty. */
    public static Optional<Model> model(String id) {
        if (id == null || id.isBlank())
            return Optional.empty();
        String trimmed = id.trim();
        return MODELS.stream().filter(model -> model.id().equals(trimmed)).findFirst();
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
}
