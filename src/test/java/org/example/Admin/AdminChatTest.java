package org.example.Admin;

import org.example.AiChat.Persona;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
import org.example.Config.BotProperties;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
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
    private static final Actor OWNER = Actor.owner(270459384L, "@vajnaya_sobaka", "Алексей");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private SettingsService settings;

    @MockitoBean
    private MessageSender sender;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void ownerAccess() {
        when(access.isOwner()).thenReturn(true);
        when(access.visibleChats()).thenReturn(Optional.empty());
        when(access.seesChat(anyLong())).thenReturn(true);
        when(access.canEditPersona(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(access.author()).thenReturn(Optional.of(OWNER));
    }

    @Test
    void rendersChatsWithCurrentPersona() throws Exception {
        when(personas.findAllChats(any())).thenReturn(List.of(new PersonaRepository.ChatSummary(
                CHAT, "Синий чат", 1, "Юра", OffsetDateTime.parse("2026-11-02T12:00:00Z"), 12)));
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));

        mockMvc.perform(get("/admin/chats").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Синий чат")))
                .andExpect(content().string(containsString("Юра")))
                .andExpect(content().string(containsString("data-messages=\"12\"")));
    }

    @Test
    void putsChatsWithPersonaFirstAndSeparatesTheRest() throws Exception {
        when(personas.findAllChats(any())).thenReturn(List.of(
                new PersonaRepository.ChatSummary(2L, "Рабочий чат", null, null, null, 0),
                new PersonaRepository.ChatSummary(CHAT, "Синий чат", 1, "Юра",
                        OffsetDateTime.parse("2026-11-02T12:00:00Z"), 0)));

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
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(personas).switchPersona(eq(CHAT), eq(1), eq("В чат приходит Юра"), any(Actor.class));
        verify(sender).sendMessage(CHAT, "В чат приходит Юра");
        verify(sender, never()).sendMessage(CHAT, "Петя покинул чат");
    }

    @Test
    void switchingPersonaSendsLeaveAndJoin() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.of(new Persona(2, "Петя", "промпт")));
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(personas).switchPersona(eq(CHAT), eq(1), eq("В чат приходит Юра"), any(Actor.class));
        verify(sender).sendMessage(CHAT, "Петя покинул чат. Убрал: @vajnaya_sobaka (Алексей)");
        verify(sender).sendMessage(CHAT, "В чат приходит Юра");
    }

    @Test
    void leaveMessageNamesTheModeratorWhoRemovedThePersona() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.author()).thenReturn(Optional.of(
                new Actor(777L, "vasya", "@clerfe", "Иван")));
        when(personas.findForChat(CHAT)).thenReturn(Optional.of(new Persona(2, "Петя", "промпт")));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(10);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "")
                        .with(user("vasya").roles("MODERATOR")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"));

        verify(sender).sendMessage(CHAT, "Петя покинул чат. Убрал: @clerfe (Иван)");
    }

    @Test
    void usesTemplatesFromSettings() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.empty());
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
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

        verify(personas).switchPersona(eq(CHAT), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(Actor.class));
        verify(sender).sendMessage(CHAT, "Петя покинул чат. Убрал: @vajnaya_sobaka (Алексей)");
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

        verify(personas, never()).switchPersona(anyLong(), any(), any(), any());
        verify(sender, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void foreignChatIsRejected() throws Exception {
        when(access.seesChat(anyLong())).thenReturn(false);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("vasya").roles("MODERATOR")).with(csrf()))
                .andExpect(redirectedUrl("/admin/chats"))
                .andExpect(flash().attribute("error", containsString("не выдан")));

        verify(personas, never()).switchPersona(anyLong(), any(), any(), any());
    }

    @Test
    void moderatorMenuHidesOwnerSections() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(personas.findAllChats(any())).thenReturn(List.of());

        String body = mockMvc.perform(get("/admin/chats").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("Воспоминания"), "меню модератора");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("/admin/settings"), "настройки скрыты");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("/admin/moderators"), "модераторы скрыты");
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("/admin/prompt"), "промпт скрыт");
    }

    @Test
    void telegramFailureKeepsBinding() throws Exception {
        when(personas.findForChat(CHAT)).thenReturn(Optional.empty());
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 3, null)));
        when(sender.sendMessage(anyLong(), anyString())).thenReturn(null);

        mockMvc.perform(post("/admin/chats/assign")
                        .param("chatId", String.valueOf(CHAT))
                        .param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Telegram не принял")));

        verify(personas).switchPersona(eq(CHAT), eq(1), anyString(), any(Actor.class));
    }
}
