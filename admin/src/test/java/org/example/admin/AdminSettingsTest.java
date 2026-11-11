package org.example.admin;

import org.example.core.aichat.ProviderKeyStatusRepository;
import org.example.core.aichat.ReactionEmoji;
import org.example.core.aichat.ReactionEmojiRepository;
import org.example.core.config.BotProperties;
import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.core.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.core.settings.Settings.AI_MEMORY_NOTIFY_OWNER;
import static org.example.core.settings.Settings.AI_MODEL_KEY;
import static org.example.core.settings.Settings.AI_REACTIONS_ENABLED;
import static org.example.core.settings.Settings.AI_REACTION_MAX_TOKENS;
import static org.example.core.settings.Settings.AI_REACTION_MODEL;
import static org.example.core.settings.Settings.AI_REACTION_MODEL_USE_OWN;
import static org.example.core.settings.Settings.AI_REACTION_PAUSE_SECONDS;
import static org.example.core.settings.Settings.AI_REACTION_POOL_SIZE;
import static org.example.core.settings.Settings.AI_REACTION_PROMPT;
import static org.example.core.settings.Settings.AI_REACTION_ROUTE;
import static org.example.core.settings.Settings.AI_ROUTE_KEY;
import static org.example.core.settings.Settings.OWNER_CHAT_ID;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSettingsController.class,
        properties = {
                "bot.admin.password=" + AdminSettingsTest.PASSWORD,
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test",
                "bot.ai.key.aitunnel=sk-tunnel"
        })
@Import({AdminSecurityConfig.class, AdminSettingsValidator.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminSettingsTest {
    static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settings;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private ProviderKeyStatusRepository providerKeys;

    @MockitoBean
    private ReactionEmojiRepository reactionEmojis;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void ownerAccess() {
        when(access.isOwner()).thenReturn(true);
        when(providerKeys.hasKey("deepseek")).thenReturn(true);
        when(providerKeys.hasKey("aitunnel")).thenReturn(true);
        org.mockito.Mockito.lenient().when(reactionEmojis.findAllForAdmin()).thenReturn(List.of(
                new ReactionEmoji("😁", "смешно, весело", false, true),
                new ReactionEmoji("✍", "сообщение попало в память", true, true)));
    }

    @Test
    void rendersStoredValues() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "deepseek-flash", "Модель для ответов")));

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deepseek-flash")))
                .andExpect(content().string(containsString("Модель")))
                .andExpect(content().string(containsString("Настройки")))
                .andExpect(content().string(containsString("data-tip=\"Модель для ответов\"")))
                .andExpect(content().string(containsString("перезапуск не нужен")));
    }

    @Test
    void groupsModelsByProviderAndMarksMissingKeys() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<optgroup")))
                .andExpect(content().string(containsString("label=\"DeepSeek\"")))
                .andExpect(content().string(containsString("label=\"OpenAI — нет ключа BOT_AI_KEY_OPENAI\"")))
                .andExpect(content().string(containsString("label=\"Grok — нет ключа BOT_AI_KEY_GROK\"")));
    }

    @Test
    void rejectsSwitchToProviderWithoutKey() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "deepseek-flash", null)));

        mockMvc.perform(formWith(Map.of(AI_MODEL_KEY, "grok-4.7")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOT_AI_KEY_GROK")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void savesOtherFieldsWhenStoredModelHasNoKey() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "grok-4.7", null)));

        mockMvc.perform(formWith(Map.of(AI_MODEL_KEY, "grok-4.7", AI_ANSWER_TEMPERATURE, "1.2")))
                .andExpect(redirectedUrl("/admin/settings?tab=model&saved"));

        verify(settings).save(AI_MODEL_KEY, "grok-4.7");
        verify(settings).save(AI_ANSWER_TEMPERATURE, "1.2");
    }

    @Test
    void hidesTooltipWhenDescriptionIsEmpty() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "deepseek-chat", null)));

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-tip=\""))));
    }

    @Test
    void showsOnlyFieldsOfTheOpenTab() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=notifications").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Уведомления")))
                .andExpect(content().string(containsString(org.example.core.settings.Settings.OWNER_CHAT_ID)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Температура ответа"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Текущая модель"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Ключи провайдеров"))));
    }

    @Test
    void modelBlocksAreShownOnlyOnModelTab() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Текущая модель")))
                .andExpect(content().string(containsString("Ключи провайдеров")))
                .andExpect(content().string(containsString("Как добавить ключ провайдера")));

        mockMvc.perform(get("/admin/settings?tab=memory").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("Как добавить ключ провайдера"))));
    }

    @Test
    void sliderShowsChosenValueInOutput() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("oninput=")))
                .andExpect(content().string(containsString("<output")));
    }

    @Test
    void planTokenLimitIsShownOnModelTab() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лимит токенов плана")));
    }

    @Test
    void grudgeSettingsAreShownOnMemoryTab() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=memory").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Обида за оскорбление")))
                .andExpect(content().string(containsString("Порог адресата колкостей")));
    }

    @Test
    void savesValidForm() throws Exception {
        mockMvc.perform(formWith(Map.of()))
                .andExpect(redirectedUrl("/admin/settings?tab=model&saved"));

        verify(settings).save(AI_MODEL_KEY, "deepseek-flash");
        verify(settings).save(AI_ANSWER_TEMPERATURE, "1.8");
    }

    @Test
    void rejectsTemperatureOutOfRange() throws Exception {
        mockMvc.perform(formWith(Map.of(AI_ANSWER_TEMPERATURE, "3")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Температура должна быть числом от 0 до 2")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void rejectsModelOutsideTheList() throws Exception {
        mockMvc.perform(formWith(Map.of(AI_MODEL_KEY, "gpt-4")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("нет в списке доступных")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void offersModeSwitchAndCascadeLists() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"aiAccess\"")))
                .andExpect(content().string(containsString("Прямой доступ")))
                .andExpect(content().string(containsString("Провайдеры")))
                .andExpect(content().string(containsString("name=\"aiProvider\"")))
                .andExpect(content().string(containsString("name=\"aiModelGroup\"")))
                .andExpect(content().string(containsString("name=\"aiDoorModel\"")))
                .andExpect(content().string(containsString("data-door=\"aitunnel|gemini\"")));
    }

    @Test
    void directListKeepsOnlyDirectProviders() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("label=\"AITunnel"))));
    }

    @Test
    void savedAggregatorModelOpensProviderMode() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_ROUTE_KEY, "aitunnel", null),
                new SettingsService.Setting(AI_MODEL_KEY, "gemini-4-argon", null)));

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"provider\" title=\"Модели через агрегатор\" checked=\"checked\"")))
                .andExpect(content().string(containsString("data-door=\"aitunnel|gemini\" selected=\"selected\"")));
    }

    @Test
    void savesAggregatorPairFromCascade() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(formWith(Map.of(
                        AdminModelForm.ACCESS_KEY, AdminModelForm.PROVIDER,
                        AdminModelForm.PROVIDER_KEY, "aitunnel",
                        AdminModelForm.GROUP_KEY, "gemini",
                        AdminModelForm.DOOR_MODEL_KEY, "gemini-4-argon",
                        AI_MODEL_KEY, "")))
                .andExpect(redirectedUrl("/admin/settings?tab=model&saved"));

        verify(settings).save(AI_ROUTE_KEY, "aitunnel");
        verify(settings).save(AI_MODEL_KEY, "gemini-4-argon");
        verify(settings, never()).save(eq(AdminModelForm.PROVIDER_KEY), anyString());
        verify(settings, never()).save(eq(AdminModelForm.GROUP_KEY), anyString());
        verify(settings, never()).save(eq(AdminModelForm.DOOR_MODEL_KEY), anyString());
        verify(settings, never()).save(eq(AdminModelForm.ACCESS_KEY), anyString());
    }

    @Test
    void directChoiceRecordsItsOwnProvider() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(formWith(Map.of(AI_MODEL_KEY, "grok-4.7",
                        AdminModelForm.ACCESS_KEY, AdminModelForm.DIRECT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOT_AI_KEY_GROK")));
    }

    @Test
    void rejectsAggregatorWithoutKey() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(formWith(Map.of(
                        AdminModelForm.ACCESS_KEY, AdminModelForm.PROVIDER,
                        AdminModelForm.PROVIDER_KEY, "venice",
                        AdminModelForm.GROUP_KEY, "anthropic",
                        AdminModelForm.DOOR_MODEL_KEY, "claude-sonnet-5-5",
                        AI_MODEL_KEY, "")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOT_AI_KEY_VENICE")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void rejectsAggregatorModelOutsideTheCascade() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(formWith(Map.of(
                        AdminModelForm.ACCESS_KEY, AdminModelForm.PROVIDER,
                        AdminModelForm.PROVIDER_KEY, "aitunnel",
                        AdminModelForm.GROUP_KEY, "gemini",
                        AdminModelForm.DOOR_MODEL_KEY, "claude-sonnet-5-5",
                        AI_MODEL_KEY, "")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("нет в списке доступных")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void explainsHowToAddProviderKeys() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Как добавить ключ провайдера")))
                .andExpect(content().string(containsString("ssh root@45.141.103.51")))
                .andExpect(content().string(containsString("secrets.VENICE_API_KEY")))
                .andExpect(content().string(containsString(">BOT_AI_KEY_VENICE<")))
                .andExpect(content().string(containsString("https://venice.ai/settings/api/keys")))
                .andExpect(content().string(containsString(">BOT_AI_KEY_DEEPSEEK<")))
                .andExpect(content().string(containsString(">BOT_AI_KEY_POLZA<")))
                .andExpect(content().string(containsString("https://polza.ai/dashboard/api-keys")))
                .andExpect(content().string(containsString(
                        "группы: DeepSeek, Grok, OpenAI, Google Gemini, Anthropic, Qwen, Kimi, GLM")));
    }

    @Test
    void polzaModelsShowPriceAndOthersDoNot() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        ">z-ai/glm-5.3 · 8.33 / 166.9 ₽ за млн<")))
                .andExpect(content().string(containsString(
                        ">sber/gigachat-2 · 93.6 / 93.6 ₽ за млн<")))
                .andExpect(content().string(containsString(">grok-4-7<")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("grok-4-7 ·"))));
    }

    @Test
    void keyHintsMarkProvidersThatAlreadyHaveKeys() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">BOT_AI_KEY_AITUNNEL<")))
                .andExpect(content().string(containsString(">BOT_AI_KEY_OPENAI<")));
    }

    @Test
    void aggregatorWithoutKeyIsMarkedInTheListItself() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Venice — нет ключа")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("AITunnel — нет ключа"))))
                .andExpect(content().string(containsString(".field[hidden]")));
    }

    @Test
    void summaryLineNamesProvidersWithAndWithoutKeys() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ключи провайдеров")))
                .andExpect(content().string(containsString("class=\"key-chip ok\"")))
                .andExpect(content().string(containsString("class=\"key-chip missing\"")))
                .andExpect(content().string(containsString("DeepSeek — есть")))
                .andExpect(content().string(containsString("AITunnel — есть")))
                .andExpect(content().string(containsString("Venice — нет")));
    }

    @Test
    void showsTheModelTheBotUsesNow() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Текущая модель")))
                .andExpect(content().string(containsString("<strong>deepseek-flash</strong>")))
                .andExpect(content().string(containsString("поставщик: DeepSeek · прямое подключение")));
    }

    @Test
    void currentModelShowsTheAggregatorDoor() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_ROUTE_KEY, "aitunnel", null),
                new SettingsService.Setting(AI_MODEL_KEY, "gemini-4-argon", null)));

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>gemini-4-argon</strong>")))
                .andExpect(content().string(containsString("поставщик: AITunnel · через агрегатор")));
    }

    @Test
    void currentModelWarnsAboutUnknownPair() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "нет-такой", null)));

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("нет в каталоге — бот ответит моделью по умолчанию deepseek-flash")));
    }

    @Test
    void modelWithoutKeyCannotBePicked() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("disabled=\"disabled\">grok-4.7</option>")))
                .andExpect(content().string(containsString("disabled=\"disabled\">claude-sonnet-5-5</option>")));
    }

    @Test
    void storedModelWithoutKeyStaysPicked() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MODEL_KEY, "grok-4.7", null)));

        mockMvc.perform(get("/admin/settings?tab=model").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(
                        "disabled=\"disabled\">grok-4.7</option>"))));
    }

    @Test
    void notificationsTabHasMemoryCheckboxOnByDefault() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=notifications").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Присылать обновления памяти")))
                .andExpect(content().string(containsString("class=\"checkbox-line\"")))
                .andExpect(content().string(containsString("<span>Присылать обновления памяти</span>")))
                .andExpect(content().string(containsString("type=\"checkbox\"")))
                .andExpect(content().string(containsString("name=\"" + AI_MEMORY_NOTIFY_OWNER + "\"")))
                .andExpect(content().string(containsString("value=\"false\"")))
                .andExpect(content().string(containsString("checked=\"checked\"")));
    }

    @Test
    void storedDisabledMemoryCheckboxStaysOff() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_MEMORY_NOTIFY_OWNER, "false", "Описание флажка")));

        mockMvc.perform(get("/admin/settings?tab=notifications").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("checked=\"checked\""))));
    }

    @Test
    void uncheckedMemoryToggleIsSavedAsFalse() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "notifications")
                        .param(OWNER_CHAT_ID, "270459384")
                        .param(AI_MEMORY_NOTIFY_OWNER, "false"))
                .andExpect(redirectedUrl("/admin/settings?tab=notifications&saved"));

        verify(settings).save(AI_MEMORY_NOTIFY_OWNER, "false");
        verify(settings).save(OWNER_CHAT_ID, "270459384");
    }

    @Test
    void reactionsTabShowsFieldsAndEmojiCatalog() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=reactions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ставить реакции на сообщения")))
                .andExpect(content().string(containsString("name=\"" + AI_REACTION_PROMPT + "\"")))
                .andExpect(content().string(containsString("name=\"" + AI_REACTION_POOL_SIZE + "\"")))
                .andExpect(content().string(containsString("name=\"" + AI_REACTION_PAUSE_SECONDS + "\"")))
                .andExpect(content().string(containsString("name=\"" + AI_REACTION_MODEL_USE_OWN + "\"")))
                .andExpect(content().string(containsString("name=\"aiReactionAccess\"")))
                .andExpect(content().string(containsString("name=\"" + AI_REACTION_MODEL + "\"")))
                .andExpect(content().string(containsString("name=\"meaning_0\"")))
                .andExpect(content().string(containsString("name=\"active_0\"")))
                .andExpect(content().string(containsString("name=\"memory_1\"")))
                .andExpect(content().string(containsString("name=\"emoji_1\" value=\"✍\"")))
                .andExpect(content().string(containsString("Сохранить список")));
    }

    /**
     * Эмодзи в имени поля ломает сохранение: Spring Security отбраковывает символы, которых
     * нет в таблицах Unicode у JVM, и отвечает 400 ещё до контроллера.
     */
    @Test
    void emojiFieldNamesAreNumeric() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        String page = mockMvc.perform(get("/admin/settings?tab=reactions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String form = page.substring(page.indexOf("/admin/settings/reactions/emojis"));
        form = form.substring(0, form.indexOf("</form>"));
        Matcher names = Pattern.compile("name=\"([^\"]*)\"").matcher(form);

        List<String> unexpected = new ArrayList<>();
        while (names.find()) {
            String name = names.group(1);
            if (!name.matches("(emoji|meaning|memory|active)_\\d+|tab|_csrf"))
                unexpected.add(name);
        }
        assertTrue(unexpected.isEmpty(), "имена полей с эмодзи: " + unexpected);
    }

    @Test
    void reactionsTabHidesModelOnlyBlocks() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=reactions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Ключи провайдеров"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Текущая модель"))));
    }

    @Test
    void activeEmojiWithoutTextIsRejected() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings/reactions/emojis")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("emoji_0", "😁")
                        .param("meaning_0", "")
                        .param("active_0", "true")
                        .param("memory_0", "false"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Укажите, когда ставим")))
                .andExpect(content().string(containsString("error-row")));

        verify(reactionEmojis, never()).saveAll(anyList());
    }

    @Test
    void inactiveEmojiMayHaveNoText() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings/reactions/emojis")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("emoji_0", "😁")
                        .param("meaning_0", "")
                        .param("active_0", "false")
                        .param("memory_0", "false"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(reactionEmojis).saveAll(anyList());
    }

    @Test
    void emojiCatalogIsSavedWithOneAction() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings/reactions/emojis")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("emoji_0", "😁")
                        .param("meaning_0", "смешно")
                        .param("active_0", "true")
                        .param("memory_0", "false")
                        .param("emoji_1", "✍")
                        .param("meaning_1", "попал в память")
                        .param("active_1", "true")
                        .param("memory_1", "true"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(reactionEmojis).saveAll(argThat(rows -> rows.size() == 2
                && rows.stream().anyMatch(row -> row.emoji().equals("✍") && row.memory() && row.active())
                && rows.stream().anyMatch(row -> row.emoji().equals("😁") && row.active() && !row.memory())));
    }

    /** Страница, открытая до выкатки, присылает старые имена полей: молча «Сохранено» не показываем. */
    @Test
    void staleEmojiFormDoesNotReportSaved() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings/reactions/emojis")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param("meaning_😁", "смешно")
                        .param("active_😁", "true")
                        .param("memory_😁", "false"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("обновите страницу")));

        verify(reactionEmojis, never()).saveAll(anyList());
    }

    /** Эмодзи вне разрешённого набора прислать нельзя: строка с чужим символом отбрасывается. */
    @Test
    void emojiOutsideCatalogIsIgnored() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings/reactions/emojis")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("emoji_0", "🚀")
                        .param("meaning_0", "чужая реакция")
                        .param("active_0", "true")
                        .param("memory_0", "false")
                        .param("emoji_1", "😁")
                        .param("meaning_1", "смешно")
                        .param("active_1", "true")
                        .param("memory_1", "false"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(reactionEmojis).saveAll(argThat(rows -> rows.size() == 1
                && rows.get(0).emoji().equals("😁") && rows.get(0).meaning().equals("смешно")));
    }

    @Test
    void reactionModelWithoutKeyIsRejected() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param(AI_REACTIONS_ENABLED, "true")
                        .param(AI_REACTION_PROMPT, "Ты читаешь переписку")
                        .param(AI_REACTION_POOL_SIZE, "20")
                        .param(AI_REACTION_PAUSE_SECONDS, "30")
                        .param(AI_REACTION_MODEL_USE_OWN, "true")
                        .param("aiReactionAccess", "direct")
                        .param(AI_REACTION_MODEL, "grok-4.7")
                        .param(AI_REACTION_ROUTE, ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BOT_AI_KEY_GROK")));

        verify(settings, never()).save(anyString(), anyString());
    }

    @Test
    void ownReactionModelFromCascadeIsSaved() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param(AI_REACTIONS_ENABLED, "true")
                        .param(AI_REACTION_PROMPT, "Ты читаешь переписку")
                        .param(AI_REACTION_POOL_SIZE, "20")
                        .param(AI_REACTION_PAUSE_SECONDS, "30")
                        .param(AI_REACTION_MAX_TOKENS, "2000")
                        .param(AI_REACTION_MODEL_USE_OWN, "true")
                        .param("aiReactionAccess", "provider")
                        .param("aiReactionProvider", "aitunnel")
                        .param("aiReactionGroup", "deepseek")
                        .param("aiReactionDoorModel", "deepseek-v4-pro"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(settings).save(AI_REACTION_ROUTE, "aitunnel");
        verify(settings).save(AI_REACTION_MODEL, "deepseek-v4-pro");
    }

    @Test
    void ownModelOffKeepsStoredPair() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param(AI_REACTIONS_ENABLED, "true")
                        .param(AI_REACTION_PROMPT, "Ты читаешь переписку")
                        .param(AI_REACTION_POOL_SIZE, "20")
                        .param(AI_REACTION_PAUSE_SECONDS, "30")
                        .param(AI_REACTION_MAX_TOKENS, "2000")
                        .param(AI_REACTION_MODEL_USE_OWN, "false"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(settings, never()).save(eq(AI_REACTION_MODEL), anyString());
        verify(settings, never()).save(eq(AI_REACTION_ROUTE), anyString());
    }

    @Test
    void reactionsTabShowsAndSavesTokenBudget() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/settings?tab=reactions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"aiReactionMaxTokens\"")))
                .andExpect(content().string(containsString("Лимит токенов прохода")));

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param(AI_REACTIONS_ENABLED, "true")
                        .param(AI_REACTION_PROMPT, "Ты читаешь переписку")
                        .param(AI_REACTION_POOL_SIZE, "20")
                        .param(AI_REACTION_PAUSE_SECONDS, "30")
                        .param(AI_REACTION_MAX_TOKENS, "2000")
                        .param(AI_REACTION_MODEL_USE_OWN, "false"))
                .andExpect(redirectedUrl("/admin/settings?tab=reactions&saved"));

        verify(settings).save(AI_REACTION_MAX_TOKENS, "2000");
    }

    @Test
    void rejectsReactionBudgetOutOfRange() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("tab", "reactions")
                        .param(AI_REACTIONS_ENABLED, "true")
                        .param(AI_REACTION_PROMPT, "Ты читаешь переписку")
                        .param(AI_REACTION_POOL_SIZE, "20")
                        .param(AI_REACTION_PAUSE_SECONDS, "30")
                        .param(AI_REACTION_MAX_TOKENS, "9000")
                        .param(AI_REACTION_MODEL_USE_OWN, "false"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Лимит токенов прохода должен быть целым от 1 до 8192")));

        verify(settings, never()).save(eq(AI_REACTION_MAX_TOKENS), anyString());
    }

    private MockHttpServletRequestBuilder formWith(Map<String, String> overrides) {
        Map<String, String> form = AdminSettingsValidatorTest.validForm();
        form.put("tab", "model");
        form.putAll(overrides);

        MockHttpServletRequestBuilder builder = post("/admin/settings")
                .with(user("admin").roles("ADMIN"))
                .with(csrf());
        form.forEach(builder::param);
        return builder;
    }
}
