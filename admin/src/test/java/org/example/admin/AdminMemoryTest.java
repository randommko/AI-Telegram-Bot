package org.example.admin;

import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.PersonaRepository;
import org.example.core.config.BotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@WebMvcTest(controllers = AdminMemoryController.class,
        properties = {
                "bot.admin.password=secret",
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import({AdminSecurityConfig.class, AdminMemoryValidator.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminMemoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemoryRepository memories;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void permissions() {
        when(access.isOwner()).thenReturn(true);
        when(access.canEditPersona(anyInt())).thenReturn(true);
        when(access.visibleChats()).thenReturn(Optional.empty());
        when(access.visiblePersonaIds()).thenReturn(Optional.empty());
    }

    @Test
    void legacyAddressRedirectsToPersonaTab() throws Exception {
        mockMvc.perform(get("/admin/memories").param("personaId", "1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin/personas/1?tab=memories"));
    }

    @Test
    void legacyAddressWithoutPersonaOpensPersonaList() throws Exception {
        mockMvc.perform(get("/admin/memories").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin/personas"));
    }

    @Test
    void legacyAddressOfForeignPersonaOpensPersonaList() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.canEditPersona(7)).thenReturn(false);

        mockMvc.perform(get("/admin/memories").param("personaId", "7")
                        .with(user("vasya").roles("MODERATOR")))
                .andExpect(redirectedUrl("/admin/personas"));
    }

    @Test
    void rendersPeopleAsCheckboxesToUnlink() throws Exception {
        when(memories.findDetails(5)).thenReturn(Optional.of(new MemoryRepository.MemoryDetails(
                5, 1, "семья", "день рождения 1 января", 10, false, null, "manual", List.of(7L))));
        when(memories.allUsers()).thenReturn(List.of(new MemoryRepository.PersonRef(7L, "@nick")));

        mockMvc.perform(get("/admin/personas/1/memories/5").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"people\"")))
                .andExpect(content().string(containsString("type=\"checkbox\"")))
                .andExpect(content().string(containsString("checked=\"checked\"")))
                .andExpect(content().string(containsString("class=\"people-search\"")))
                .andExpect(content().string(containsString("class=\"people-list\"")))
                .andExpect(content().string(containsString("data-search=\"@nick\"")));
    }

    @Test
    void foreignPersonaInPathDoesNotOpenMemory() throws Exception {
        when(memories.findDetails(5)).thenReturn(Optional.of(new MemoryRepository.MemoryDetails(
                5, 1, "семья", "день рождения 1 января", 10, false, null, "manual", List.of())));

        mockMvc.perform(get("/admin/personas/2/memories/5").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin/personas/2?tab=memories"))
                .andExpect(flash().attribute("error", containsString("не найдено")));
    }

    @Test
    void moderatorCannotOpenMemoryForm() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.canEditPersona(1)).thenReturn(false);

        mockMvc.perform(get("/admin/personas/1/memories/new")
                        .with(user("vasya").roles("MODERATOR")))
                .andExpect(redirectedUrl("/admin/personas/1"))
                .andExpect(flash().attribute("error", containsString("недоступна")));
    }

    @Test
    void savesManualMemory() throws Exception {
        when(memories.categories()).thenReturn(Set.of("работа", "семья", "другое"));

        mockMvc.perform(saveForm())
                .andExpect(redirectedUrl("/admin/personas/1?tab=memories"));

        verify(memories).createManual(eq(1), eq("работа"), eq("играет в шахматы"), eq(7), eq(false),
                eq(LocalDate.of(2024, 3, 21)), eq(List.of(42L)));
    }

    @Test
    void rejectsImportanceOutOfRange() throws Exception {
        when(memories.categories()).thenReturn(Set.of("работа", "другое"));

        mockMvc.perform(saveForm("importance", "11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Важность")));

        verify(memories, never()).createManual(anyInt(), anyString(), anyString(), anyInt(),
                any(Boolean.class), any(), any());
    }

    @Test
    void formPersonaMustMatchPath() throws Exception {
        mockMvc.perform(post("/admin/personas/2/memories")
                        .param("personaId", "1")
                        .param("category", "работа")
                        .param("content", "играет в шахматы")
                        .param("importance", "7")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas/2?tab=memories"))
                .andExpect(flash().attribute("error", containsString("другой персоне")));

        verify(memories, never()).createManual(anyInt(), anyString(), anyString(), anyInt(),
                any(Boolean.class), any(), any());
    }

    @Test
    void requiresSeparateDeleteConfirmation() throws Exception {
        mockMvc.perform(post("/admin/personas/1/memories/5/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas/1/memories/5/delete"));

        verify(memories, never()).deleteManual(anyInt());
    }

    @Test
    void deletesAfterConfirmation() throws Exception {
        when(memories.findDetails(5)).thenReturn(Optional.of(
                new MemoryRepository.MemoryDetails(5, 1, "семья", "день рождения 1 января", 10,
                        true, LocalDate.of(1990, 1, 1), "manual", List.of())));

        mockMvc.perform(post("/admin/personas/1/memories/5/delete")
                        .param("confirm", "true")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas/1?tab=memories"));

        verify(memories).deleteManual(eq(5));
    }

    @Test
    void restoresDeletedMemoryFromTab() throws Exception {
        when(memories.findAnyDetails(5)).thenReturn(Optional.of(
                new MemoryRepository.MemoryDetails(5, 1, "семья", "день рождения 1 января", 10,
                        true, LocalDate.of(1990, 1, 1), "manual", List.of())));

        mockMvc.perform(post("/admin/personas/1/memories/5/restore")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas/1?tab=memories&deleted=true"));

        verify(memories).restoreManual(eq(5));
    }

    @Test
    void showsCoreWarningOnDeletePage() throws Exception {
        when(memories.findDetails(5)).thenReturn(Optional.of(
                new MemoryRepository.MemoryDetails(5, 1, "семья", "день рождения 1 января", 10,
                        true, LocalDate.of(1990, 1, 1), "manual", List.of())));

        mockMvc.perform(get("/admin/personas/1/memories/5/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("не восстановит")));
    }

    private MockHttpServletRequestBuilder saveForm(String... overrides) {
        Map<String, String> values = new HashMap<>(Map.of(
                "personaId", "1",
                "category", "работа",
                "content", "играет в шахматы",
                "importance", "7",
                "eventDate", "2024-03-21"));
        for (int i = 0; i + 1 < overrides.length; i += 2)
            values.put(overrides[i], overrides[i + 1]);

        MockHttpServletRequestBuilder builder = post("/admin/personas/1/memories")
                .with(user("admin").roles("ADMIN"))
                .with(csrf());
        values.forEach(builder::param);
        return builder.param("people", "42");
    }
}
