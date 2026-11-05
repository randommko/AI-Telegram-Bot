package org.example.admin;

import org.example.aichat.PersonaRepository;
import org.example.aichat.PromptComposer;
import org.example.config.BotProperties;
import org.example.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.example.settings.Settings.AI_PROMPT_TECHNICAL;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

@WebMvcTest(controllers = AdminPromptController.class,
        properties = {
                "bot.admin.password=" + AdminPromptTest.PASSWORD,
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import(AdminSecurityConfig.class)
@EnableConfigurationProperties(BotProperties.class)
class AdminPromptTest {
    static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settings;
    @MockitoBean
    private PromptComposer composer;
    @MockitoBean
    private PersonaRepository personas;
    @MockitoBean
    private AdminAccess access;
    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void pageShowsSectionsInAssemblyOrderWithCounter() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_PROMPT_TECHNICAL, "Техника из базы", "Описание")));
        when(personas.findAllChats(any())).thenReturn(List.of(
                new PersonaRepository.ChatSummary(5L, "Чат с Юрой", 2, "Юра", OffsetDateTime.now(), 0),
                new PersonaRepository.ChatSummary(6L, "Чат без персоны", null, null, OffsetDateTime.now(), 0)));

        mockMvc.perform(get("/admin/prompt").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Техника из базы")))
                .andExpect(content().string(containsString("Кто ты")))
                .andExpect(content().string(containsString("символов")))
                .andExpect(content().string(containsString("Вернуть по умолчанию")))
                .andExpect(content().string(containsString("С персоной")))
                .andExpect(content().string(containsString("Без персоны")))
                .andExpect(content().string(containsString("Чат с Юрой")))
                .andExpect(content().string(containsString("Чат без персоны")))
                .andExpect(content().string(containsString("Промпт")))
                .andExpect(content().string(containsString("Эмоции")));
    }

    @Test
    void changedTemplateIsSaved() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/prompt").with(user("admin").roles("ADMIN")).with(csrf())
                        .param(AI_PROMPT_TECHNICAL, "Новый текст"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/prompt"));

        verify(settings).save(AI_PROMPT_TECHNICAL, "Новый текст");
    }

    @Test
    void unchangedTemplateIsNotRewritten() throws Exception {
        when(settings.listAll()).thenReturn(List.of(
                new SettingsService.Setting(AI_PROMPT_TECHNICAL, "Тот же текст", null)));

        mockMvc.perform(post("/admin/prompt").with(user("admin").roles("ADMIN")).with(csrf())
                        .param(AI_PROMPT_TECHNICAL, "Тот же текст"))
                .andExpect(status().is3xxRedirection());

        verify(settings, never()).save(eq(AI_PROMPT_TECHNICAL), any());
    }

    @Test
    void resetWritesDefaultText() throws Exception {
        when(settings.listAll()).thenReturn(List.of());

        mockMvc.perform(post("/admin/prompt").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("reset", AI_PROMPT_TECHNICAL))
                .andExpect(status().is3xxRedirection());

        verify(settings).save(eq(AI_PROMPT_TECHNICAL), org.mockito.ArgumentMatchers.contains("Ты участник этого чата"));
    }

    @Test
    void previewShowsComposedPromptAndChangesNothing() throws Exception {
        when(personas.findAllChats(any())).thenReturn(List.of(
                new PersonaRepository.ChatSummary(5L, "Чат", 2, "Юра", OffsetDateTime.now(), 0)));
        when(composer.compose(5L, true)).thenReturn(
                new PromptComposer.ComposedPrompt(List.of(), "СОБРАННЫЙ ПРОМПТ"));

        mockMvc.perform(get("/admin/prompt/preview?chatId=5").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("СОБРАННЫЙ ПРОМПТ")))
                .andExpect(content().string(containsString("Тестовый вопрос")));

        verify(composer).compose(5L, true);
        verify(settings, never()).save(any(), any());
    }

    @Test
    void previewOfEmptyDatabaseDoesNotCallComposer() throws Exception {
        when(personas.findAllChats(any())).thenReturn(List.of());

        mockMvc.perform(get("/admin/prompt/preview").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("нет чатов")));

        verify(composer, never()).compose(org.mockito.ArgumentMatchers.anyLong(), anyBoolean());
    }
}
