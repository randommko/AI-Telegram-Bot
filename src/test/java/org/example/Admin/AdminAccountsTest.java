package org.example.Admin;

import org.example.AiChat.PersonaRepository;
import org.example.Config.BotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAccountsController.class,
        properties = {
                "bot.admin.password=secret",
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import(AdminSecurityConfig.class)
@EnableConfigurationProperties(BotProperties.class)
class AdminAccountsTest {
    private static final long CHAT = -1001347887839L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccountsRepository accounts;

    @MockitoBean
    private ChatModeratorRepository moderators;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void owner() {
        when(access.isOwner()).thenReturn(true);
        when(access.author()).thenReturn(Optional.of(
                Actor.owner(270459384L, "@vajnaya_sobaka", "Алексей")));
    }

    @Test
    void listsAccountsWithPersonAndChats() throws Exception {
        when(accounts.findAll()).thenReturn(List.of(new AdminAccountsRepository.AdminAccount(
                1, 777L, "vasya", "@clerfe", "Иван", String.valueOf(CHAT), List.of(CHAT),
                java.time.OffsetDateTime.now(), null)));

        mockMvc.perform(get("/admin/moderators").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vasya")))
                .andExpect(content().string(containsString("@clerfe")))
                .andExpect(content().string(containsString("не входил")));
    }

    @Test
    void rejectsAdminLogin() throws Exception {
        mockMvc.perform(post("/admin/moderators")
                        .param("person", "@clerfe")
                        .param("login", "admin")
                        .param("password", "modpass")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"))
                .andExpect(flash().attribute("error", containsString("закреплён за владельцем")));

        verify(accounts, never()).create(anyLong(), any(), any(), any());
    }

    @Test
    void rejectsUnknownPerson() throws Exception {
        when(accounts.findPerson("@nobody")).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/moderators")
                        .param("person", "@nobody")
                        .param("login", "vasya")
                        .param("password", "modpass")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Человек не найден")));

        verify(accounts, never()).create(anyLong(), any(), any(), any());
    }

    @Test
    void createsAccountWithHashedPassword() throws Exception {
        when(accounts.findPerson("@clerfe")).thenReturn(Optional.of(
                new AdminAccountsRepository.AdminPerson(777L, "@clerfe", "Иван")));
        when(accounts.personTaken(777L)).thenReturn(false);
        when(accounts.loginTaken("vasya")).thenReturn(false);

        mockMvc.perform(post("/admin/moderators")
                        .param("person", "@clerfe")
                        .param("login", "vasya")
                        .param("password", "modpass")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(accounts).create(eq(777L), eq("vasya"), argThat(hash -> hash.startsWith("{bcrypt}")), any());
    }

    @Test
    void grantsChatToModerator() throws Exception {
        when(accounts.tgUserIdOf(1)).thenReturn(Optional.of(777L));
        when(personas.findAllChats(any())).thenReturn(List.of(new PersonaRepository.ChatSummary(
                CHAT, "Синий чат", 1, "Юра", null, 0)));

        mockMvc.perform(post("/admin/moderators/1/chats")
                        .param("chatId", String.valueOf(CHAT))
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(moderators).grant(eq(777L), eq(CHAT), eq(270459384L));
    }

    @Test
    void revokesChatFromModerator() throws Exception {
        when(accounts.tgUserIdOf(1)).thenReturn(Optional.of(777L));

        mockMvc.perform(post("/admin/moderators/1/chats/" + CHAT + "/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(moderators).revoke(777L, CHAT);
    }
}
