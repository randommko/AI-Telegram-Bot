package org.example.Admin;

import org.example.Config.BotProperties;
import org.example.Settings.SettingsService;
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

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_MODEL_KEY;
import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
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
                "bot.ai.key=sk-test"
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
                new SettingsService.Setting(AI_MODEL_KEY, "deepseek-chat", "Модель для ответов")));

        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deepseek-chat")))
                .andExpect(content().string(containsString("Модель")))
                .andExpect(content().string(containsString("Настройки")))
                .andExpect(content().string(containsString("data-tip=\"Модель для ответов\"")))
                .andExpect(content().string(containsString("перезапуск не нужен")));
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
                .andExpect(content().string(containsString(org.example.Settings.Settings.OWNER_CHAT_ID)))
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
    void rejectsTemplateWithoutNamePlaceholder() throws Exception {
        mockMvc.perform(formWith(Map.of(AI_PERSONA_JOIN_TEMPLATE, "В чат приходит гость", "tab", "templates")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Шаблон входа должен содержать {name}")));

        verify(settings, never()).save(anyString(), anyString());
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
