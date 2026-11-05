package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionRepository;
import org.example.core.aichat.FieldNoteRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@WebMvcTest(controllers = AdminPersonaController.class,
        properties = {
                "bot.admin.password=secret",
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import({AdminSecurityConfig.class, AdminPersonaValidator.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminPersonaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonaRepository personas;

    @MockitoBean
    private EmotionRepository emotions;

    @MockitoBean
    private FieldNoteRepository fieldNotes;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void catalog() {
        when(access.isOwner()).thenReturn(true);
        when(access.canSeePersona(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(access.canEditPersona(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(access.visibleChats()).thenReturn(Optional.empty());
        when(emotions.findCatalog()).thenReturn(CATALOG);
        when(fieldNotes.findAll()).thenReturn(Map.of(
                "aggressionThreshold", "Порог, при котором персона срывается",
                "replyDelayMs", "Пауза между репликами"));
    }

    @Test
    void rendersPersonaList() throws Exception {
        when(personas.findAllWithCounts(any())).thenReturn(List.of(
                new PersonaSummary(1, "Юра", 5, 26, "-1001347887839")));

        mockMvc.perform(get("/admin/personas").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Юра")))
                .andExpect(content().string(containsString("26")));
    }

    @Test
    void rendersExistingPersona() throws Exception {
        when(personas.findDetails(1)).thenReturn(Optional.of(details(1)));

        mockMvc.perform(get("/admin/personas/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ты Юра")))
                .andExpect(content().string(containsString("юр")));
    }

    @Test
    void createsPersona() throws Exception {
        mockMvc.perform(saveForm())
                .andExpect(redirectedUrl("/admin/personas"));

        verify(personas).create(any(PersonaDetails.class));
    }

    @Test
    void rejectsScaleOutOfRange() throws Exception {
        mockMvc.perform(saveForm("emotionValues[радость]", "11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Радость")));

        verify(personas, never()).create(any());
    }

    @Test
    void rendersEmotionFieldForEveryEditableScale() throws Exception {
        mockMvc.perform(get("/admin/personas/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Гнев (0..10)")))
                .andExpect(content().string(containsString("emotionValues[гнев]")))
                .andExpect(content().string(containsString("Злость и резкость")))
                .andExpect(content().string(containsString("class=\"grid-2\"")));
    }

    @Test
    void showsLiveDataNoteOnCardButNotInList() throws Exception {
        when(personas.findAllWithCounts(any())).thenReturn(List.of());

        mockMvc.perform(get("/admin/personas/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("перезапуск не нужен")));

        mockMvc.perform(get("/admin/personas").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("перезапуск не нужен"))));
    }

    @Test
    void slidersShowChosenValueAndSecondBlockIsSeparate() throws Exception {
        mockMvc.perform(get("/admin/personas/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("oninput=")))
                .andExpect(content().string(containsString("<output")))
                .andExpect(content().string(containsString("class=\"grid-3\"")))
                .andExpect(content().string(containsString("<legend>Срыв и ответы</legend>")))
                .andExpect(content().string(containsString("возвращается к ним с течением времени")));
    }

    @Test
    void showsTipsFromCatalogAndFieldNotes() throws Exception {
        mockMvc.perform(get("/admin/personas/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tip=\"Злость и резкость\"")))
                .andExpect(content().string(containsString("data-tip=\"Порог, при котором персона срывается\"")));
    }

    @Test
    void emotionErrorOpensEmotionsTab() throws Exception {
        mockMvc.perform(saveForm("emotionValues[гнев]", "11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tab=\"emotions\"")))
                .andExpect(content().string(containsString("Гнев: допустимо от 0 до 10")));

        verify(personas, never()).create(any());
    }

    @Test
    void nameErrorOpensGeneralTab() throws Exception {
        mockMvc.perform(saveForm("name", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tab=\"general\"")))
                .andExpect(content().string(containsString("Имя не может быть пустым")));

        verify(personas, never()).create(any());
    }

    @Test
    void rejectsAliasThatIsACommand() throws Exception {
        mockMvc.perform(saveForm("aliases", "clear"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("командой")));

        verify(personas, never()).create(any());
    }

    @Test
    void moderatorCannotSaveSharedPersona() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.canEditPersona(1)).thenReturn(false);

        mockMvc.perform(saveForm("id", "1"))
                .andExpect(redirectedUrl("/admin/personas/1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("error", containsString("только смотреть")));

        verify(personas, never()).update(any());
    }

    @Test
    void moderatorCreatesPersonaInHisChat() throws Exception {
        when(access.isOwner()).thenReturn(false);
        when(access.seesChat(-100200L)).thenReturn(true);

        mockMvc.perform(saveForm("chatId", "-100200"))
                .andExpect(redirectedUrl("/admin/personas"));

        verify(personas).createInChat(any(PersonaDetails.class), eq(-100200L));
        verify(personas, never()).create(any(PersonaDetails.class));
    }

    @Test
    void refusesToDeletePersonaBoundToChat() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of(-1001347887839L));

        mockMvc.perform(post("/admin/personas/1/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("error"));

        verify(personas, never()).delete(anyInt());
    }

    @Test
    void deletesPersonaWithoutChats() throws Exception {
        when(personas.chatsOf(1)).thenReturn(List.of());

        mockMvc.perform(post("/admin/personas/1/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/personas"));

        verify(personas).delete(eq(1));
    }

    private MockHttpServletRequestBuilder saveForm(String... overrides) {
        Map<String, String> values = new HashMap<>(Map.ofEntries(
                Map.entry("name", "Юра"),
                Map.entry("prompt", "Ты Юра"),
                Map.entry("aliases", "юр, юра"),
                Map.entry("emotionValues[радость]", "5"),
                Map.entry("emotionValues[гнев]", "2"),
                Map.entry("emotionValues[сарказм]", "5"),
                Map.entry("aggressionThreshold", "8"),
                Map.entry("hysteriaReplies", "4"),
                Map.entry("hysteriaCooldownHours", "6"),
                Map.entry("maxReplies", "4"),
                Map.entry("replyDelayMs", "1500"),
                Map.entry("initiativeEnabled", "true"),
                Map.entry("initiativeCooldownMessages", "20")));
        for (int i = 0; i + 1 < overrides.length; i += 2)
            values.put(overrides[i], overrides[i + 1]);

        MockHttpServletRequestBuilder builder = post("/admin/personas")
                .with(user("admin").roles("ADMIN"))
                .with(csrf());
        values.forEach(builder::param);
        return builder;
    }

    private PersonaDetails details(int id) {
        return new PersonaDetails(id, "Юра", "Ты Юра", List.of("юр", "юра"),
                Map.of("радость", 5, "гнев", 2, "сарказм", 5), 8, 4, 6, 4, 1500, true, 20);
    }

    private static final List<Emotion> CATALOG = List.of(
            new Emotion("радость", "Радость", "Подъём, желание шутить", "плюс", -0.25, 1.00, 5, false, 10),
            new Emotion("гнев", "Гнев", "Злость и резкость", "минус", 0.55, 1.50, 2, false, 30),
            new Emotion("сарказм", "Сарказм", "Ирония как манера речи", "стиль", 0.15, 0.50, 5, false, 110),
            new Emotion("агрессия", "Агрессия", "Производная шкала", "стиль", 0, 0, 0, true, 999));
}
