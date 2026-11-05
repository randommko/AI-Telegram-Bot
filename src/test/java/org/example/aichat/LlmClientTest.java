package org.example.aichat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.config.BotProperties;
import org.example.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SettingsService settings = mock(SettingsService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final HttpResponse<String> response = mockResponse();

    private final AtomicReference<HttpRequest> sent = new AtomicReference<>();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LlmClient.class);

    private LlmClient client;

    @BeforeEach
    void setUp() throws Exception {
        logger.addAppender(logs);
        logs.start();

        when(settings.getInt(AI_MAX_TOKENS_ANSWER_QUESTION, 1000)).thenReturn(1000);
        when(httpClient.send(any(), any())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return response;
        });
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void sendsRequestToProviderOfConfiguredModel() {
        client = clientWith(Map.of("deepseek", "sk-deep", "grok", "sk-grok"));
        when(settings.getModel()).thenReturn("grok-4.7");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(answer("привет"));

        assertThat(client.sendRequestToAi(messages(), 1.0f)).isEqualTo("привет");

        HttpRequest request = sent.get();
        assertThat(request.uri().toString()).isEqualTo("https://api.x.ai/v1/chat/completions");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer sk-grok");
        assertThat(request.timeout()).contains(LlmCatalog.provider("grok").orElseThrow().timeout());
    }

    @Test
    void doesNotSendRequestWhenProviderHasNoKey() throws Exception {
        client = clientWith(Map.of("deepseek", "sk-deep"));
        when(settings.getModel()).thenReturn("grok-4.7");

        assertThat(client.sendRequestToAi(messages(), 1.0f)).isNull();

        verify(httpClient, never()).send(any(), any());
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("BOT_AI_KEY_GROK"));
    }

    @Test
    void unknownModelIsSentAsDefaultModel() throws Exception {
        client = clientWith(Map.of("deepseek", "sk-deep"));
        when(settings.getModel()).thenReturn("deepseek-chat");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(answer("ок"));

        client.sendRequestToAi(messages(), 1.0f);

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertThat(request.getValue().uri().toString()).isEqualTo("https://api.deepseek.com/v1/chat/completions");
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("deepseek-chat").contains("нет в каталоге"));
    }

    @Test
    void tokenLimitIsTruncatedToModelCeiling() {
        client = clientWith(Map.of("deepseek", "sk-deep"));
        LlmCatalog.Model small = new LlmCatalog.Model("малая", "deepseek", "Малая", true, 120);

        ObjectNode request = client.request(small, messages(), 1.0f, 4000, false);

        assertThat(request.get("max_tokens").asInt()).isEqualTo(120);
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("урезан").contains("120"));
    }

    @Test
    void jsonModeIsRequestedOnlyForModelsThatSupportIt() {
        client = clientWith(Map.of("deepseek", "sk-deep", "gemini", "sk-gem"));
        LlmCatalog.Model json = LlmCatalog.model("deepseek-flash").orElseThrow();
        LlmCatalog.Model plain = LlmCatalog.model("gemini-4-argon").orElseThrow();

        assertThat(client.request(json, messages(), 1.0f, 1000, true).has("response_format")).isTrue();
        assertThat(client.request(plain, messages(), 1.0f, 1000, true).has("response_format")).isFalse();
    }

    @Test
    void nonJsonProviderAnswerStillEndsWithReply() {
        client = clientWith(Map.of("gemini", "sk-gem"));
        when(settings.getModel()).thenReturn("gemini-4-argon");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(answer("[\"раз\"]"));

        assertThat(client.sendJsonRequestToAi(messages(), 1.0f)).isEqualTo("[\"раз\"]");

        HttpRequest request = sent.get();
        assertThat(request.uri().toString()).startsWith("https://generativelanguage.googleapis.com/");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer sk-gem");
    }

    @Test
    void rejectedJsonModeIsRetriedWithoutIt() throws Exception {
        client = clientWith(Map.of("deepseek", "sk-deep"));
        when(settings.getModel()).thenReturn("deepseek-flash");
        when(response.statusCode()).thenReturn(400).thenReturn(200);
        when(response.body()).thenReturn("response_format unsupported", answer("[\"раз\"]"));

        assertThat(client.sendJsonRequestToAi(messages(), 1.0f)).isEqualTo("[\"раз\"]");
        verify(httpClient, org.mockito.Mockito.times(2)).send(any(), any());
    }

    @Test
    void errorLogNamesProviderAndModel() {
        client = clientWith(Map.of("deepseek", "sk-deep", "grok", "sk-grok"));
        when(settings.getModel()).thenReturn("grok-4.7");
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("internal error");

        assertThat(client.sendRequestToAi(messages(), 1.0f)).isNull();

        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("Grok ").contains("grok-4.7"));
        assertThat(logs.list).noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains("DeepSeek"));
    }

    @Test
    void sendsRequestToTheDoorFromSettings() {
        client = clientWith(Map.of("gemini", "sk-gem", "aitunnel", "sk-tunnel"));
        when(settings.getModel()).thenReturn("gemini-4-argon");
        when(settings.getRoute()).thenReturn("aitunnel");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(answer("привет"));

        assertThat(client.sendRequestToAi(messages(), 1.0f)).isEqualTo("привет");

        HttpRequest request = sent.get();
        assertThat(request.uri().toString()).isEqualTo("https://api.aitunnel.ru/v1/chat/completions");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer sk-tunnel");
    }

    @Test
    void missingModelBehindTheDoorFallsBackToDefault() {
        client = clientWith(Map.of("deepseek", "sk-deep", "venice", "sk-ven"));
        when(settings.getModel()).thenReturn("gemini-4-argon");
        when(settings.getRoute()).thenReturn("grok");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(answer("ок"));

        client.sendRequestToAi(messages(), 1.0f);

        assertThat(sent.get().uri().toString()).isEqualTo("https://api.deepseek.com/v1/chat/completions");
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("grok").contains("нет в каталоге"));
    }

    @Test
    void veniceRequestDisablesItsSystemPromptAndUsesCompletionTokensField() {
        client = clientWith(Map.of("venice", "sk-ven"));
        LlmCatalog.Model model = LlmCatalog.model("venice", "claude-sonnet-5-5").orElseThrow();

        ObjectNode request = client.request(model, messages(), 1.0f, 1000, true);

        assertThat(request.path("venice_parameters").path("include_venice_system_prompt").asBoolean(true)).isFalse();
        assertThat(request.path("venice_parameters").path("strip_thinking_response").asBoolean(false)).isTrue();
        assertThat(request.has("max_completion_tokens")).isTrue();
        assertThat(request.has("max_tokens")).isFalse();
    }

    @Test
    void directRequestKeepsPlainBody() {
        client = clientWith(Map.of("deepseek", "sk-deep"));
        LlmCatalog.Model model = LlmCatalog.model("deepseek-flash").orElseThrow();

        ObjectNode request = client.request(model, messages(), 1.0f, 1000, true);

        assertThat(request.has("max_tokens")).isTrue();
        assertThat(request.has("max_completion_tokens")).isFalse();
        assertThat(request.has("venice_parameters")).isFalse();
    }

    @Test
    void contentAsArrayOfBlocksIsNotLost() {
        client = clientWith(Map.of("venice", "sk-ven"));
        when(settings.getModel()).thenReturn("gemini-4-argon");
        when(settings.getRoute()).thenReturn("venice");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(blocks("{\"replies\": ", "[\"раз\"]}"));

        assertThat(client.sendJsonRequestToAi(messages(), 1.0f)).isEqualTo("{\"replies\": [\"раз\"]}");
    }

    @Test
    void emptyContentStaysEmptyAndIsLogged() {
        client = clientWith(Map.of("venice", "sk-ven"));
        when(settings.getModel()).thenReturn("gemini-4-argon");
        when(settings.getRoute()).thenReturn("venice");
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(blocks());

        assertThat(client.sendRequestToAi(messages(), 1.0f)).isEmpty();
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("пустой ответ").contains("finish_reason"));
    }

    private LlmClient clientWith(Map<String, String> keys) {
        BotProperties properties = new BotProperties("1:dummy", null, new BotProperties.Ai(keys), null, null);
        return new LlmClient(properties, settings, httpClient);
    }

    private ArrayNode messages() {
        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put("role", "user").put("content", "привет"));
        return messages;
    }

    private String answer(String content) {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return root.toString();
    }

    /** Ответ, в котором содержимое пришло массивом текстовых блоков, как у Venice. */
    private String blocks(String... parts) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        for (String part : parts) {
            ObjectNode block = content.addObject();
            block.put("type", "text");
            block.put("text", part);
        }

        ObjectNode message = root.putArray("choices").addObject()
                .put("finish_reason", "stop")
                .putObject("message");
        message.set("content", content);
        return root.toString();
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse() {
        return mock(HttpResponse.class);
    }
}
