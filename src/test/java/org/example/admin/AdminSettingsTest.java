package org.example.admin;

import org.example.config.BotProperties;
import org.example.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.example.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.settings.Settings.AI_MEMORY_NOTIFY_OWNER;
import static org.example.settings.Settings.AI_MODEL_KEY;
import static org.example.settings.Settings.AI_ROUTE_KEY;
import static org.example.settings.Settings.OWNER_CHAT_ID;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
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
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void ownerAccess() {
        when(access.isOwner()).thenReturn(true);
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
                .andExpect(content().string(containsString(org.example.settings.Settings.OWNER_CHAT_ID)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Температура ответа"))));
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
                .andExpect(content().string(containsString(
                        "группы: DeepSeek, Grok, OpenAI, Google Gemini, Anthropic, Qwen, Kimi, GLM")));
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
