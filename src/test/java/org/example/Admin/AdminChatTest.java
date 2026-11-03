package org.example.Admin;

import org.example.AiChat.Persona;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
import org.example.Config.BotProperties;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminChatController.class,
        properties = {
                "bot.admin.password=secret",
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key=sk-test"
        })
@Import(AdminSecurityConfig.class)
@EnableConfigurationProperties(BotProperties.class)
class AdminChatTest {
    private static final long CHAT = -1001347887839L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private SettingsService settings;

    @MockitoBean
    private MessageSender sender;

    @Test
    void rendersChatsWithCurrentPersona() throws Exception {
        when(personas.findAllChats()).thenReturn(List.of(new PersonaRepository.ChatSummary(
                CHAT, "Синий чат", 1, "Юра", OffsetDateTime.parse("2026-11-02T12:00:00Z"))));

        mockMvc.perform(get("/admin/chats").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Синий чат")))
                .andExpect(content().string(containsString("Юра")));
    }

    @Test
    void putsChatsWithPersonaFirstAndSeparatesTheRest() throws Exception {
        when(personas.findAllChats()).thenReturn(List.of(
                new PersonaRepository.ChatSummary(2L, "Рабочий чат", null, null, null),
                new PersonaRepository.ChatSummary(CHAT, "Синий чат", 1, "Юра",
                        OffsetDateTime.parse("2026-11-02T12:00:00Z"))));

        String body = mockMvc.perform(get("/admin/chats").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.indexOf("С персоной") < body.indexOf("Без персоны"),
                "группа с персоной идёт первой");
        org.junit.jupiter.api.Assertions.assertTrue(body.indexOf("Синий чат") < body.indexOf("Рабочий чат"),
                "чат с персоной идёт выше чата без персоны");
    }

    @Test
    void firstAssignmentSendsOnlyJoin() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.empty());
        when(personas.findAllWithCounts()).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(personas).switchPersona(CHAT, 1, "В чат приходит Юра");
        verify(sender).sendMessage(CHAT, "В чат приходит Юра");
        verify(sender, never()).sendMessage(CHAT, "Петя покинул чат");
    }

    @Test
    void switchingPersonaSendsLeaveAndJoin() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.of(new Persona(2, "Петя", "промпт")));
        when(personas.findAllWithCounts()).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(personas).switchPersona(CHAT, 1, "В чат приходит Юра");
        verify(sender).sendMessage(CHAT, "Петя покинул чат");
        verify(sender).sendMessage(CHAT, "В чат приходит Юра");
    }

    @Test
    void usesTemplatesFromSettings() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.empty());
        when(personas.findAllWithCounts()).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(settings.getSettingValue("aiPersonaJoinTemplate")).thenReturn("Заходит {name}");
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(sender).sendMessage(CHAT, "Заходит Юра");
    }

    @Test
    void removalSendsOnlyLeaveAndKeepsHistory() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.of(new Persona(2, "Петя", "промпт")));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(personas).switchPersona(CHAT, null, null);
        verify(sender).sendMessage(CHAT, "Петя покинул чат");
    }

    @Test
    void samePersonaChangesNothing() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.of(new Persona(1, "Юра", "промпт")));

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"))
                .andExpect(flash().attribute("saved", containsString("уже эта персона")));

        verify(personas, never()).switchPersona(anyLong(), any(), any());
        verify(sender, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void telegramFailureKeepsBinding() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.empty());
        when(personas.findAllWithCounts()).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(null);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Telegram не принял")));

        verify(personas).switchPersona(eq(CHAT), eq(1), anyString());
    }
}
