package org.example.admin;

import org.example.core.aichat.PersonaDetails;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSummary;
import org.example.core.config.BotProperties;
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
import java.util.Map;
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
    private ModeratorPersonaRepository personaGrants;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void owner() {
        when(access.isOwner()).thenReturn(true);
        when(access.authorId()).thenReturn(Optional.of(270459384L));
    }

    @Test
    void listsAccountsWithPersonAndChatTitle() throws Exception {
        when(accounts.findAll()).thenReturn(List.of(account(List.of(CHAT))));
        when(personas.findAllChats(any())).thenReturn(List.of(chat()));
        when(personas.findAllWithCounts(any())).thenReturn(List.of(
                new PersonaSummary(5, "Юра", 0, 0, null),
                new PersonaSummary(6, "Гоша", 0, 0, null),
                new PersonaSummary(7, "Лена", 0, 0, null)));
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of(
                new ModeratorPersonaRepository.PersonaAccessRow(5, "Юра",
                        ModeratorPersonaRepository.Source.CREATED),
                new ModeratorPersonaRepository.PersonaAccessRow(6, "Гоша",
                        ModeratorPersonaRepository.Source.GRANTED),
                new ModeratorPersonaRepository.PersonaAccessRow(7, "Лена",
                        ModeratorPersonaRepository.Source.CHAT)));

        mockMvc.perform(get("/admin/moderators").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vasya")))
                .andExpect(content().string(containsString("@clerfe")))
                .andExpect(content().string(containsString("Синий чат")))
                .andExpect(content().string(containsString("не входил")))
                .andExpect(content().string(containsString("name=\"chatIds\"")))
                .andExpect(content().string(containsString("name=\"personaIds\"")))
                .andExpect(content().string(containsString("— создал")))
                .andExpect(content().string(containsString("— через чат")))
                .andExpect(content().string(containsString(">Сохранить</button>")));
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

        verify(accounts, never()).create(anyLong(), any(), any());
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

        verify(accounts, never()).create(anyLong(), any(), any());
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

        verify(accounts).create(eq(777L), eq("vasya"), argThat(hash -> hash.startsWith("{bcrypt}")));
    }

    @Test
    void grantsChatAndPersonaInOneSave() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of())));
        when(personas.findAllChats(any())).thenReturn(List.of(chat()));
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of());
        when(personas.findDetails(5)).thenReturn(Optional.of(persona(5)));

        mockMvc.perform(post("/admin/moderators/1/access")
                        .param("chatIds", String.valueOf(CHAT))
                        .param("personaIds", "5")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(moderators).grant(eq(777L), eq(CHAT), eq(270459384L));
        verify(personaGrants).grant(eq(777L), eq(5), eq(270459384L));
    }

    @Test
    void revokesChatAndPersonaInOneSave() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of(CHAT))));
        when(personas.findAllChats(any())).thenReturn(List.of(chat()));
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of(
                new ModeratorPersonaRepository.PersonaAccessRow(5, "Юра",
                        ModeratorPersonaRepository.Source.GRANTED)));

        mockMvc.perform(post("/admin/moderators/1/access")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(moderators).revoke(777L, CHAT);
        verify(personaGrants).revoke(777L, 5);
    }

    @Test
    void createdPersonaIsNotRevokedByTheForm() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of(CHAT))));
        when(personas.findAllChats(any())).thenReturn(List.of(chat()));
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of(
                new ModeratorPersonaRepository.PersonaAccessRow(5, "Юра",
                        ModeratorPersonaRepository.Source.CREATED)));

        mockMvc.perform(post("/admin/moderators/1/access")
                        .param("chatIds", String.valueOf(CHAT))
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"));

        verify(personaGrants, never()).revoke(777L, 5);
    }

    @Test
    void refusesUnknownChat() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of())));
        when(personas.findAllChats(any())).thenReturn(List.of());

        mockMvc.perform(post("/admin/moderators/1/access")
                        .param("chatIds", String.valueOf(CHAT))
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"))
                .andExpect(flash().attribute("error", containsString("не видел")));

        verify(moderators, never()).grant(anyLong(), anyLong(), any());
    }

    @Test
    void refusesUnknownPersona() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of())));
        when(personas.findAllChats(any())).thenReturn(List.of());
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of());
        when(personas.findDetails(99)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/moderators/1/access")
                        .param("personaIds", "99")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"))
                .andExpect(flash().attribute("error", containsString("не найдена")));

        verify(personaGrants, never()).grant(anyLong(), eq(99), any());
    }

    @Test
    void refusesPersonaGrantedOnlyThroughChat() throws Exception {
        when(accounts.findById(1)).thenReturn(Optional.of(account(List.of(CHAT))));
        when(personas.findAllChats(any())).thenReturn(List.of(chat()));
        when(personaGrants.accessOf(anyLong(), any())).thenReturn(List.of(
                new ModeratorPersonaRepository.PersonaAccessRow(5, "Юра",
                        ModeratorPersonaRepository.Source.CHAT)));
        when(personas.findDetails(5)).thenReturn(Optional.of(persona(5)));

        mockMvc.perform(post("/admin/moderators/1/access")
                        .param("chatIds", String.valueOf(CHAT))
                        .param("personaIds", "5")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/moderators"))
                .andExpect(flash().attribute("error", containsString("не в этой форме")));

        verify(personaGrants, never()).grant(anyLong(), eq(5), any());
    }

    private static AdminAccountsRepository.AdminAccount account(List<Long> chatIds) {
        String chats = String.join(", ", chatIds.stream().map(String::valueOf).toList());
        return new AdminAccountsRepository.AdminAccount(
                1, 777L, "vasya", "@clerfe", "Иван", chats, chatIds, OffsetDateTime.now(), null);
    }

    private static PersonaRepository.ChatSummary chat() {
        return new PersonaRepository.ChatSummary(CHAT, "Синий чат", 1, "Юра", null, 0);
    }

    private PersonaDetails persona(int id) {
        return new PersonaDetails(id, "Юра", "промпт", List.of("юра"), Map.of(),
                8, 4, 6, 4, 1500, true, 20);
    }
}
