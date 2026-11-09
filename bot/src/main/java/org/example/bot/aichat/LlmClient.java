package org.example.bot.aichat;

import org.example.core.aichat.LlmCatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.core.config.BotProperties;
import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.core.settings.Settings.DEFAULT_AI_MODEL;

/**
 * Запросы к LLM в формате Chat Completions. Провайдер выбирается по модели из настроек
 * на каждый запрос: адрес, ключ, таймаут и потолок токенов берутся из {@link LlmCatalog}.
 */
@Component
public class LlmClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_TOKENS = 1000;
    /** Потолок настройки: больше админка не предложит. Потолок модели может быть ниже. */
    private static final int SETTINGS_MAX_TOKENS = 8192;

    private final Logger logger = LoggerFactory.getLogger(LlmClient.class);

    private final BotProperties properties;
    private final SettingsService settings;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public LlmClient(BotProperties properties, SettingsService settings) {
        this(properties, settings, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    /** Второй конструктор нужен тестам: транспорт подменяется без сети. */
    LlmClient(BotProperties properties, SettingsService settings, HttpClient httpClient) {
        this.properties = properties;
        this.settings = settings;
        this.httpClient = httpClient;
    }

    /** Модель и лимит токенов читаются из настроек на каждый запрос: правка действует без рестарта. */
    public String sendRequestToAi(ArrayNode messages, Float temperature) {
        return sendRequestToAi(messages, temperature, maxTokens());
    }

    public String sendRequestToAi(ArrayNode messages, Float temperature, int maxTokens) {
        return send(messages, temperature, maxTokens, false);
    }

    /** Принудительный JSON-режим: провайдер сам гарантирует разбираемый ответ. */
    public String sendJsonRequestToAi(ArrayNode messages, Float temperature) {
        return send(messages, temperature, maxTokens(), true);
    }

    public String sendJsonRequestToAi(ArrayNode messages, Float temperature, int maxTokens) {
        return send(messages, temperature, maxTokens, true);
    }

    /**
     * Запрос с явной парой «маршрут + модель»: так проход чтения может считать
     * реакции дешёвой моделью, не меняя модель ответов. Пустая модель — основная.
     */
    public String sendJsonRequestToAi(String modelId, String route, ArrayNode messages,
                                      Float temperature, int maxTokens) {
        String id = modelId == null ? "" : modelId.trim();
        if (id.isEmpty())
            return sendJsonRequestToAi(messages, temperature, maxTokens);

        return send(messages, temperature, maxTokens, true, resolveModel(route, id));
    }

    private String send(ArrayNode messages, Float temperature, int maxTokens, boolean jsonMode) {
        return send(messages, temperature, maxTokens, jsonMode, resolveModel());
    }

    private String send(ArrayNode messages, Float temperature, int maxTokens, boolean jsonMode,
                        LlmCatalog.Model model) {
        LlmCatalog.Provider provider = LlmCatalog.providerOf(model)
                .orElseThrow(() -> new IllegalStateException("У модели " + model.id() + " нет провайдера в каталоге"));

        String apiKey = properties.ai().keyFor(provider.id());
        if (apiKey == null) {
            logger.error("Нет ключа провайдера {} ({}): модель {} не ответит",
                    provider.title(), provider.envName(), model.id());
            return null;
        }

        try {
            HttpResponse<String> response = post(request(model, messages, temperature, maxTokens, jsonMode), provider, apiKey);
            if (response.statusCode() == 400 && jsonMode && model.jsonMode()) {
                logger.warn("{} отклонил JSON-режим (модель {}), повтор без него: {}",
                        provider.title(), model.id(), response.body());
                return send(messages, temperature, maxTokens, false, model);
            }
            if (response.statusCode() != 200) {
                logger.error("{} вернул {} (модель {}): {}",
                        provider.title(), response.statusCode(), model.id(), response.body());
                return null;
            }
            return content(response.body(), provider, model);
        } catch (Exception e) {
            logger.error("{} не смог ответить моделью {}: {}", provider.title(), model.id(), e.getMessage());
            return null;
        }
    }

    /** Модель из общих настроек; неизвестная пара не глушит бота — уходим на модель по умолчанию. */
    private LlmCatalog.Model resolveModel() {
        String configured = settings.getModel();
        String route = settings.getRoute();
        route = route == null ? "" : route.trim();
        Optional<LlmCatalog.Model> model = LlmCatalog.model(route, configured);
        if (model.isPresent())
            return model.get();

        if (route.isEmpty())
            logger.error("Модели «{}» нет в каталоге: используется {}", configured, DEFAULT_AI_MODEL);
        else
            logger.error("Модели «{}» нет в каталоге провайдера «{}»: используется {}",
                    configured, route, DEFAULT_AI_MODEL);

        return LlmCatalog.model(DEFAULT_AI_MODEL)
                .orElseThrow(() -> new IllegalStateException("В каталоге нет модели по умолчанию " + DEFAULT_AI_MODEL));
    }

    /** Модель реакции; неизвестная пара откатывается на основную модель, как и у ответа. */
    private LlmCatalog.Model resolveModel(String route, String modelId) {
        String door = route == null ? "" : route.trim();
        Optional<LlmCatalog.Model> model = LlmCatalog.model(door, modelId);
        if (model.isPresent())
            return model.get();

        if (door.isEmpty())
            logger.error("Модели реакций «{}» нет в каталоге: используется основная модель", modelId);
        else
            logger.error("Модели реакций «{}» нет в каталоге провайдера «{}»: используется основная модель",
                    modelId, door);
        return resolveModel();
    }

    /** Пакетный доступ нужен тесту: тело запроса проверяется без сети. */
    ObjectNode request(LlmCatalog.Model model, ArrayNode messages, Float temperature, int maxTokens,
                       boolean jsonMode) {
        int limit = Math.min(maxTokens, model.maxOutputTokens());
        if (limit < maxTokens)
            logger.info("Лимит токенов {} урезан до {} по потолку модели {}", maxTokens, limit, model.id());

        LlmCatalog.Provider provider = LlmCatalog.providerOf(model).orElse(null);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model.id());
        request.set("messages", messages);
        request.put("temperature", temperature);
        request.put(provider == null ? "max_tokens" : provider.maxTokensField(), limit);
        if (provider != null && !provider.extraBody().isEmpty())
            request.setAll((ObjectNode) objectMapper.valueToTree(provider.extraBody()));
        if (jsonMode && model.jsonMode())
            request.set("response_format", objectMapper.createObjectNode().put("type", "json_object"));
        return request;
    }

    private int maxTokens() {
        int configured = settings.getInt(AI_MAX_TOKENS_ANSWER_QUESTION, DEFAULT_MAX_TOKENS);
        if (configured < 1 || configured > SETTINGS_MAX_TOKENS) {
            logger.warn("Лимит токенов {} вне диапазона 1..{}: используется {}",
                    configured, SETTINGS_MAX_TOKENS, DEFAULT_MAX_TOKENS);
            return DEFAULT_MAX_TOKENS;
        }
        return configured;
    }

    private HttpResponse<String> post(ObjectNode requestBody, LlmCatalog.Provider provider, String apiKey)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provider.baseUrl()))
                .timeout(provider.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String content(String responseBody, LlmCatalog.Provider provider, LlmCatalog.Model model) throws IOException {
        ObjectNode respNode = objectMapper.readValue(responseBody, ObjectNode.class);
        JsonNode choice = respNode.get("choices").get(0);
        String content = text(choice.get("message").get("content"));

        if (content.isEmpty()) {
            JsonNode usage = respNode.path("usage");
            logger.warn("{} (модель {}) вернул пустой ответ: finish_reason={}, prompt_tokens={}, completion_tokens={}, reasoning_tokens={}",
                    provider.title(), model.id(),
                    choice.path("finish_reason").asText("unknown"),
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("completion_tokens_details").path("reasoning_tokens").asInt());
        }
        return content;
    }

    /** Содержимое приходит строкой или массивом текстовых блоков: агрегаторы отдают обе формы. */
    private String text(JsonNode node) {
        if (node == null || node.isNull())
            return "";
        if (node.isTextual())
            return node.asText().trim();
        if (!node.isArray())
            return "";

        StringBuilder text = new StringBuilder();
        for (JsonNode block : node) {
            JsonNode value = block.path("text");
            text.append(value.isTextual() ? value.asText() : block.asText(""));
        }
        return text.toString().trim();
    }
}
