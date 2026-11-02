package org.example.Admin;

import org.example.AiChat.PersonaDetails;
import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PersonaSummary;
import org.example.Config.BotProperties;
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
                "bot.ai.key=sk-test"
        })
@Import({AdminSecurityConfig.class, AdminPersonaValidator.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminPersonaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonaRepository personas;

    @Test
    void rendersPersonaList() throws Exception {
        when(personas.findAllWithCounts()).thenReturn(List.of(
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
        mockMvc.perform(saveForm("baseJoy", "11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Радость")));

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

        verify(personas).delete(1);
    }

    private MockHttpServletRequestBuilder saveForm(String... overrides) {
        Map<String, String> values = new HashMap<>(Map.ofEntries(
                Map.entry("name", "Юра"),
                Map.entry("prompt", "Ты Юра"),
                Map.entry("aliases", "юр, юра"),
                Map.entry("baseJoy", "5"),
                Map.entry("baseAnger", "2"),
                Map.entry("baseOffence", "0"),
                Map.entry("baseFatigue", "3"),
                Map.entry("baseSarcasm", "5"),
                Map.entry("offenceDecayPerHour", "1"),
                Map.entry("angerThreshold", "8"),
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
                5, 2, 0, 3, 5, 1, 8, 4, 6, 4, 1500, true, 20);
    }
}
