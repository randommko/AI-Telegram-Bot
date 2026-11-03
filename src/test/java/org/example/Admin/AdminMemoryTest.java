package org.example.Admin;

import org.example.AiChat.AiMemoryExtractor;
import org.example.AiChat.MemoryRecord;
import org.example.AiChat.MemoryRepository;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                "bot.ai.key=sk-test"
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
    private AiMemoryExtractor extractor;

    @MockitoBean
    private SettingsService settings;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ChangeJournal journal;

    @BeforeEach
    void permissions() {
        when(access.isOwner()).thenReturn(true);
        when(access.canEditPersona(anyInt())).thenReturn(true);
        when(access.visibleChats()).thenReturn(java.util.Optional.empty());
        when(access.visiblePersonaIds()).thenReturn(java.util.Optional.empty());
        when(access.author()).thenReturn(java.util.Optional.of(
                Actor.owner(270459384L, "@vajnaya_sobaka", "Алексей")));
    }

    @Test
    void rendersMemoriesWithCoreBadge() throws Exception {
        when(memories.search(any())).thenReturn(new MemoryRepository.MemoryPage(
                List.of(new MemoryRecord(1, "работа", "играет в шахматы", 7, true,
                        LocalDate.of(2024, 3, 21), List.of("@clerfe"), "manual", Instant.EPOCH)),
                1, 1, 25));
        when(personas.findAllWithCounts(any())).thenReturn(List.of(new PersonaSummary(1, "Юра", 5, 26, null)));

        mockMvc.perform(get("/admin/memories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("играет в шахматы")))
                .andExpect(content().string(containsString("ядро")))
                .andExpect(content().string(containsString("Найдено: 1")));
    }

    @Test
    void rendersPeopleAsCheckboxesToUnlink() throws Exception {
        when(memories.findDetails(5)).thenReturn(java.util.Optional.of(new MemoryRepository.MemoryDetails(
                5, 1, "семья", "день рождения 1 января", 10, false, null, "manual", List.of(7L))));
        when(memories.allUsers()).thenReturn(List.of(new MemoryRepository.PersonRef(7L, "@nick")));

        mockMvc.perform(get("/admin/memories/5").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"people\"")))
                .andExpect(content().string(containsString("type=\"checkbox\"")))
                .andExpect(content().string(containsString("checked=\"checked\"")));
    }

    @Test
    void passesFiltersToRepository() throws Exception {
        when(memories.search(any())).thenReturn(new MemoryRepository.MemoryPage(List.of(), 0, 1, 25));

        mockMvc.perform(get("/admin/memories")
                        .param("personaId", "3")
                        .param("category", "семья")
                        .param("personId", "42")
                        .param("q", "шахмат")
                        .param("coreOnly", "true")
                        .param("sort", "date")
                        .param("page", "2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<MemoryRepository.MemoryFilter> filter =
                org.mockito.ArgumentCaptor.forClass(MemoryRepository.MemoryFilter.class);
        verify(memories).search(filter.capture());

        org.junit.jupiter.api.Assertions.assertEquals(3, filter.getValue().personaId());
        org.junit.jupiter.api.Assertions.assertEquals("семья", filter.getValue().category());
        org.junit.jupiter.api.Assertions.assertEquals(42L, filter.getValue().personId());
        org.junit.jupiter.api.Assertions.assertEquals("шахмат", filter.getValue().text());
        org.junit.jupiter.api.Assertions.assertTrue(filter.getValue().coreOnly());
        org.junit.jupiter.api.Assertions.assertEquals("date", filter.getValue().sort());
        org.junit.jupiter.api.Assertions.assertEquals(2, filter.getValue().page());
    }

    @Test
    void savesManualMemory() throws Exception {
        when(memories.categories()).thenReturn(Set.of("работа", "семья", "другое"));

        mockMvc.perform(saveForm())
                .andExpect(redirectedUrl("/admin/memories"));

        verify(memories).createManual(eq(1), eq("работа"), eq("играет в шахматы"), eq(7), eq(false),
                eq(LocalDate.of(2024, 3, 21)), eq(List.of(42L)), any());
    }

    @Test
    void rejectsImportanceOutOfRange() throws Exception {
        when(memories.categories()).thenReturn(Set.of("работа", "другое"));

        mockMvc.perform(saveForm("importance", "11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Важность")));

        verify(memories, never()).createManual(anyInt(), anyString(), anyString(), anyInt(),
                any(Boolean.class), any(), any(), any());
    }

    @Test
    void requiresSeparateDeleteConfirmation() throws Exception {
        mockMvc.perform(post("/admin/memories/5/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/memories/5/delete"));

        verify(memories, never()).deleteManual(anyInt(), any());
    }

    @Test
    void deletesAfterConfirmation() throws Exception {
        when(memories.findDetails(5)).thenReturn(java.util.Optional.of(
                new MemoryRepository.MemoryDetails(5, 1, "семья", "день рождения 1 января", 10,
                        true, LocalDate.of(1990, 1, 1), "manual", List.of())));

        mockMvc.perform(post("/admin/memories/5/delete")
                        .param("confirm", "true")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/memories"));

        verify(memories).deleteManual(eq(5), any());
    }

    @Test
    void moderatorFilterCarriesOnlyHisPersonas() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.visibleChats()).thenReturn(java.util.Optional.of(List.of(-100200L)));
        when(access.visiblePersonaIds()).thenReturn(java.util.Optional.of(List.of(2)));
        when(memories.search(any())).thenReturn(new MemoryRepository.MemoryPage(List.of(), 0, 1, 25));

        mockMvc.perform(get("/admin/memories").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<MemoryRepository.MemoryFilter> filter =
                org.mockito.ArgumentCaptor.forClass(MemoryRepository.MemoryFilter.class);
        verify(memories).search(filter.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(2), filter.getValue().visiblePersonas());
    }

    @Test
    void restoresDeletedMemoryFromJournal() throws Exception {
        when(journal.find(9)).thenReturn(java.util.Optional.of(new ChangeJournal.Entry(9,
                java.time.OffsetDateTime.now(), "admin", ChangeJournal.MEMORY, 5, null, 1,
                Map.of("deleted", false), Map.of("deleted", true))));

        mockMvc.perform(post("/admin/memories/restore/9")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/memories"));

        verify(memories).restoreManual(eq(5), any());
    }

    @Test
    void showsCoreWarningOnDeletePage() throws Exception {
        when(memories.findDetails(5)).thenReturn(java.util.Optional.of(
                new MemoryRepository.MemoryDetails(5, 1, "семья", "день рождения 1 января", 10,
                        true, LocalDate.of(1990, 1, 1), "manual", List.of())));

        mockMvc.perform(get("/admin/memories/5/delete").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("не восстановит")));
    }

    @Test
    void analyzeShowsNumbers() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of(-1001347887839L));
        when(extractor.analyzeChat(any(), anyInt())).thenReturn(
                new AiMemoryExtractor.AnalysisResult(10, 2, 1, 0, 1));

        mockMvc.perform(post("/admin/memories/analyze").param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/memories?personaId=1"))
                .andExpect(flash().attribute("saved", containsString("Разобрано сообщений: 10")))
                .andExpect(flash().attribute("saved", containsString("добавлено: 2")))
                .andExpect(flash().attribute("saved", containsString("отброшено удалений защищённых записей: 1")));
    }

    @Test
    void analyzeReportsNoNewMessages() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of(-1001347887839L));
        when(extractor.analyzeChat(any(), anyInt())).thenReturn(
                new AiMemoryExtractor.AnalysisResult(0, 0, 0, 0, 0));

        mockMvc.perform(post("/admin/memories/analyze").param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("saved", containsString("Новых сообщений нет")));
    }

    @Test
    void analyzeReportsMissingChat() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of());

        mockMvc.perform(post("/admin/memories/analyze").param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("нет привязанного чата")));

        verify(extractor, never()).analyzeChat(any(), anyInt());
    }

    @Test
    void analyzeReportsModelFailure() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of(-1001347887839L));
        when(extractor.analyzeChat(any(), anyInt())).thenThrow(new IllegalStateException("модель молчит"));

        mockMvc.perform(post("/admin/memories/analyze").param("personaId", "1")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("модель молчит")));
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

        MockHttpServletRequestBuilder builder = post("/admin/memories")
                .with(user("admin").roles("ADMIN"))
                .with(csrf());
        values.forEach(builder::param);
        return builder.param("people", "42");
    }
}
