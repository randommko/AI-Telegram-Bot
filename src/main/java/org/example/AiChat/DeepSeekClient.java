package org.example.AiChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.Config.BotProperties;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.DEEP_SEEK_BASE_URL;
import static org.example.Settings.Settings.DEFAULT_AI_MODEL;

@Component
public class DeepSeekClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_MAX_TOKENS = 1000;
    private static final int MAX_ALLOWED_TOKENS = 8192;

    private final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);

    private final String deepSeekApiKey;
    private final SettingsService settings;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekClient(BotProperties properties, SettingsService settings) {
        this.deepSeekApiKey = properties.ai().key();
        this.settings = settings;
    }

    /** Модель и лимит токенов читаются из настроек на каждый запрос: правка действует без рестарта. */
    public String sendRequestToAi(ArrayNode messages, Float temperature) {
        return sendRequestToAi(messages, temperature, maxTokens());
    }

    public String sendRequestToAi(ArrayNode messages, Float temperature, int maxTokens) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model());

            request.set("messages", messages);
            request.put("temperature", temperature);
            request.put("max_tokens", maxTokens);

            return sendHttpRequest(request);
        } catch (Exception e) {
            logger.error("AI не смог ответить: {}", e.getMessage());
            return null;
        }
    }

    private String model() {
        String configured = settings.getModel();
        return configured == null || configured.isBlank() ? DEFAULT_AI_MODEL : configured;
    }

    private int maxTokens() {
        int configured = settings.getInt(AI_MAX_TOKENS_ANSWER_QUESTION, DEFAULT_MAX_TOKENS);
        if (configured < 1 || configured > MAX_ALLOWED_TOKENS) {
            logger.warn("Лимит токенов {} вне диапазона 1..{}: используется {}",
                    configured, MAX_ALLOWED_TOKENS, DEFAULT_MAX_TOKENS);
            return DEFAULT_MAX_TOKENS;
        }
        return configured;
    }

    private String sendHttpRequest(ObjectNode requestBody) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEEP_SEEK_BASE_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepSeekApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("DeepSeek API error: {}", response.body());
            return null;
        }

        ObjectNode respNode = objectMapper.readValue(response.body(), ObjectNode.class);
        JsonNode choice = respNode.get("choices").get(0);
        String content = choice.get("message").get("content").asText("").trim();

        if (content.isEmpty()) {
            JsonNode usage = respNode.path("usage");
            logger.warn("AI вернул пустой ответ: finish_reason={}, prompt_tokens={}, completion_tokens={}, reasoning_tokens={}",
                    choice.path("finish_reason").asText("unknown"),
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("completion_tokens_details").path("reasoning_tokens").asInt());
        }
        return content;
    }
}
