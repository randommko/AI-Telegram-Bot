package org.example.Admin;

import org.example.AiChat.Emotion;
import org.example.AiChat.EmotionPhrase;
import org.example.AiChat.EmotionRepository;
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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
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

@WebMvcTest(controllers = AdminEmotionsController.class,
        properties = {
                "bot.admin.password=" + AdminEmotionsTest.PASSWORD,
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import({AdminSecurityConfig.class, AdminEmotionsValidator.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminEmotionsTest {
    static final String PASSWORD = "secret";

    private static final Emotion ANGER = new Emotion("гнев", "Гнев", "Злость", "минус",
            0.55, 1.5, 2, false, 30);
    private static final Emotion AGGRESSION = new Emotion("агрессия", "Агрессия", "Производная", "стиль",
            0, 0, 0, true, 999);
    private static final Map<String, List<EmotionPhrase>> PHRASES = Map.of(
            "гнев", List.of(new EmotionPhrase("гнев", 5, 6, "Ты злишься")));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmotionRepository emotions;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @BeforeEach
    void ownerAccess() {
        when(access.isOwner()).thenReturn(true);
    }

    @Test
    void pageShowsCatalogAndPhrases() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(PHRASES);

        mockMvc.perform(get("/admin/emotions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Гнев")))
                .andExpect(content().string(containsString("Ты злишься")))
                .andExpect(content().string(containsString("вычисляется из остальных")))
                .andExpect(content().string(containsString("5-6")));
    }

    @Test
    void validSaveUpdatesCatalogAndPhrases() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(PHRASES);

        mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("title_гнев", "Злость")
                        .param("weight_гнев", "0.6")
                        .param("decay_гнев", "2")
                        .param("base_гнев", "3")
                        .param("phrase_гнев_5", "Новая фраза"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/emotions"));

        verify(emotions).updateEmotion("гнев", "Злость", 0.6, 2.0, 3);
        verify(emotions).updatePhrase("гнев", 5, "Новая фраза");
    }

    @Test
    void invalidWeightReturnsFormWithError() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(PHRASES);

        mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("weight_гнев", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Допустимо")));

        verify(emotions, never()).updateEmotion(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), anyInt());
        verify(emotions, never()).updatePhrase(anyString(), anyInt(), anyString());
    }
}
