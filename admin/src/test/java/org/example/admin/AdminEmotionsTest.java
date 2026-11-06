package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionPhrase;
import org.example.core.aichat.EmotionRepository;
import org.example.core.config.BotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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

    private static final Emotion JOY = new Emotion("радость", "Радость", "Подъём, желание шутить", "плюс",
            0.45, 1.0, 5, false, 10);
    private static final Emotion ANGER = new Emotion("гнев", "Гнев", "Злость, резкость, импульсивность", "минус",
            0.55, 1.5, 2, false, 30);
    private static final Emotion AGGRESSION = new Emotion("агрессия", "Агрессия", "Производная", "стиль",
            0, 0, 0, true, 999);
    private static final Map<String, List<EmotionPhrase>> PHRASES = Map.of(
            "гнев", List.of(new EmotionPhrase("гнев", 5, 6, "Ты злишься")));
    private static final Map<String, List<EmotionPhrase>> ALL_PHRASES = Map.of(
            "радость", List.of(new EmotionPhrase("радость", 5, 6, "Тебе приятно")),
            "гнев", List.of(new EmotionPhrase("гнев", 5, 6, "Ты злишься")),
            "агрессия", List.of(new EmotionPhrase("агрессия", 9, 10, "Полный психоз")));

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
    void phraseFieldsAreShortAndParametersHaveHints() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("class=\"phrase\""), "у фраз свой короткий класс");
        assertTrue(html.contains("rows=\"2\""), "фраза занимает две строки");
        assertTrue(html.contains("Насколько эмоция усиливает агрессию"), "подсказка веса");
        assertTrue(html.contains("Сколько единиц эмоция теряет за час"), "подсказка затухания");
        assertTrue(html.contains("Значение, к которому эмоция возвращается со временем"), "подсказка базы");
    }

    @Test
    void eachTabKeepsPhrasesOfItsOwnEmotion() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertTrue(section(html, "гнев").contains("Ты злишься"), "фраза гнева в его вкладке");
        assertFalse(section(html, "гнев").contains("Тебе приятно"), "фраза радости не в вкладке гнева");
        assertTrue(section(html, "радость").contains("Тебе приятно"), "фраза радости в её вкладке");
    }

    @Test
    void derivedTabHasOnlyPhrases() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);
        String aggression = section(html, "агрессия");

        assertTrue(aggression.contains("Полный психоз"), "фразы агрессии на её вкладке");
        assertTrue(aggression.contains("вычисляется из остальных"), "пометка про вычисляемое значение");
        assertFalse(aggression.contains("weight_агрессия"), "веса у производной шкалы нет");
        assertFalse(aggression.contains("base_агрессия"), "базового значения у производной шкалы нет");
        assertTrue(section(html, "гнев").contains("weight_гнев"), "у обычной эмоции поле веса есть");
    }

    @Test
    void requestedTabIsOpen() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String anger = page("/admin/emotions?tab=гнев").getContentAsString(StandardCharsets.UTF_8);
        assertFalse(sectionHidden(anger, "гнев"), "запрошенная вкладка открыта");
        assertTrue(sectionHidden(anger, "радость"), "первая вкладка скрыта");

        String first = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);
        assertFalse(sectionHidden(first, "радость"), "без параметра открыта первая шкала каталога");
        assertTrue(sectionHidden(first, "гнев"), "вторая шкала скрыта");

        String unknown = page("/admin/emotions?tab=нет-такой").getContentAsString(StandardCharsets.UTF_8);
        assertFalse(sectionHidden(unknown, "радость"), "неизвестный код открывает первую шкалу");
    }

    @Test
    void weightSumAndCeilingFollowCatalog() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Сумма положительных весов в агрессию: 1.00"), "сумма весов");
        assertTrue(html.contains("максимум агрессии 10 из 10"), "потолок при сумме 1.0");

        Emotion lightJoy = new Emotion("радость", "Радость", "Подъём, желание шутить", "плюс",
                0.30, 1.0, 5, false, 10);
        when(emotions.findCatalog()).thenReturn(List.of(lightJoy, ANGER));

        String lighter = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);
        assertTrue(lighter.contains("Сумма положительных весов в агрессию: 0.85"), "новая сумма");
        assertTrue(lighter.contains("максимум агрессии 8 из 10"), "потолок округляется вниз");
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
                .andExpect(redirectedUrlPattern("/admin/emotions?tab=*"));

        verify(emotions).updateEmotion("гнев", "Злость", 0.6, 2.0, 3);
        verify(emotions).updatePhrase("гнев", 5, "Новая фраза");
    }

    @Test
    void saveReturnsToTheSameTab() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        MockHttpServletResponse response = mockMvc.perform(post("/admin/emotions")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("tab", "гнев")
                        .param("title_радость", "Радость")
                        .param("weight_радость", "0.45")
                        .param("decay_радость", "1")
                        .param("base_радость", "5")
                        .param("phrase_радость_5", "Тебе приятно")
                        .param("title_гнев", "Гнев")
                        .param("weight_гнев", "0.55")
                        .param("decay_гнев", "1.5")
                        .param("base_гнев", "2")
                        .param("phrase_гнев_5", "Ты злишься"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();

        String location = response.getRedirectedUrl();
        assertTrue(location != null && location.startsWith("/admin/emotions?tab="), "редирект с вкладкой: " + location);
        String code = URLDecoder.decode(location.substring("/admin/emotions?tab=".length()), StandardCharsets.UTF_8);
        assertEquals("гнев", code, "редирект несёт код открытой вкладки");
        assertFalse(sectionHidden(page("/admin/emotions?tab=" + code).getContentAsString(StandardCharsets.UTF_8),
                "гнев"), "после сохранения открыта та же вкладка");
    }

    @Test
    void saveWithoutTabReturnsToFirstScale() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        MockHttpServletResponse response = mockMvc.perform(post("/admin/emotions")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("title_радость", "Радость")
                        .param("weight_радость", "0.45")
                        .param("decay_радость", "1")
                        .param("base_радость", "5")
                        .param("phrase_радость_5", "Тебе приятно")
                        .param("title_гнев", "Гнев")
                        .param("weight_гнев", "0.55")
                        .param("decay_гнев", "1.5")
                        .param("base_гнев", "2")
                        .param("phrase_гнев_5", "Ты злишься"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();

        String code = URLDecoder.decode(
                response.getRedirectedUrl().substring("/admin/emotions?tab=".length()), StandardCharsets.UTF_8);
        assertEquals("радость", code, "без tab редирект ведёт на первую шкалу каталога");
        assertFalse(sectionHidden(page("/admin/emotions?tab=" + code).getContentAsString(StandardCharsets.UTF_8),
                "радость"), "без tab открыта первая шкала каталога");
    }

    @Test
    void saveWritesEveryEmotionFromTheForm() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("tab", "радость")
                        .param("title_радость", "Радость")
                        .param("weight_радость", "0.45")
                        .param("decay_радость", "1")
                        .param("base_радость", "5")
                        .param("phrase_радость_5", "Тебе приятно")
                        .param("title_гнев", "Гнев")
                        .param("weight_гнев", "0.55")
                        .param("decay_гнев", "1.5")
                        .param("base_гнев", "2")
                        .param("phrase_гнев_5", "Ты злишься"))
                .andExpect(status().is3xxRedirection());

        verify(emotions).updateEmotion(eq("радость"), eq("Радость"), eq(0.45), eq(1.0), eq(5));
        verify(emotions).updateEmotion(eq("гнев"), eq("Гнев"), eq(0.55), eq(1.5), eq(2));
        verify(emotions).updatePhrase("радость", 5, "Тебе приятно");
        verify(emotions).updatePhrase("гнев", 5, "Ты злишься");
    }

    @Test
    void errorOpensTabOfTheEmotionWithTheError() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("tab", "радость")
                        .param("title_радость", "Радость")
                        .param("weight_радость", "0.45")
                        .param("decay_радость", "1")
                        .param("base_радость", "5")
                        .param("phrase_радость_5", "Тебе приятно")
                        .param("title_гнев", "Гнев")
                        .param("weight_гнев", "2")
                        .param("decay_гнев", "1.5")
                        .param("base_гнев", "2")
                        .param("phrase_гнев_5", "Ты злишься"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Допустимо от -1.0 до 1.0")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertFalse(sectionHidden(html, "гнев"), "ошибка открывает вкладку своей эмоции");
        assertTrue(sectionHidden(html, "радость"), "запрошенная вкладка скрыта");
    }

    @Test
    void everyTabWithAnErrorIsMarked() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("tab", "радость")
                        .param("title_радость", "Радость")
                        .param("weight_радость", "0.45")
                        .param("decay_радость", "1")
                        .param("base_радость", "11")
                        .param("phrase_радость_5", "Тебе приятно")
                        .param("title_гнев", "Гнев")
                        .param("weight_гнев", "2")
                        .param("decay_гнев", "1.5")
                        .param("base_гнев", "2")
                        .param("phrase_гнев_5", "Ты злишься"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(2, count(html, "class=\"error\">!</span>"), "обе вкладки с ошибками помечены");
        assertFalse(sectionHidden(html, "радость"), "активна вкладка первой ошибки");
    }

    @Test
    void pageIsBuiltFromTabs() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("class=\"tabs\""), "навигация вкладок");
        assertTrue(html.contains("Полоса 5-6"), "подпись полосы у фразы");
        assertTrue(sectionHidden(html, "агрессия"), "неактивная вкладка скрыта");
        assertTrue(section(html, "гнев").contains("Полоса 5-6"), "полоса рядом со своей фразой");
    }

    @Test
    void tabShowsEmotionDescription() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertTrue(section(html, "гнев").contains("Злость, резкость, импульсивность"), "описание эмоции на вкладке");
    }

    @Test
    void aggressionNoteAppearsOnlyInsideItsTab() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertEquals(1, count(html, "вычисляется из остальных"), "пометка встречается один раз");
        assertTrue(section(html, "агрессия").contains("вычисляется из остальных"), "пометка внутри вкладки агрессии");
    }

    @Test
    void pageHasOneFormAndOneSaveButton() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(JOY, ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(ALL_PHRASES);

        String html = page("/admin/emotions").getContentAsString(StandardCharsets.UTF_8);

        assertEquals(1, count(html, "class=\"setting\""), "форма справочника одна");
        assertEquals(1, count(html, "Сохранить</button>"), "кнопка сохранения одна");
    }

    @Test
    void invalidWeightReturnsFormWithError() throws Exception {
        when(emotions.findCatalog()).thenReturn(List.of(ANGER, AGGRESSION));
        when(emotions.findPhrases()).thenReturn(PHRASES);

        mockMvc.perform(post("/admin/emotions").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("weight_гнев", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Допустимо")));

        verify(emotions, never()).updateEmotion(anyString(), anyString(), anyDouble(), anyDouble(), anyInt());
        verify(emotions, never()).updatePhrase(anyString(), anyInt(), anyString());
    }

    private MockHttpServletResponse page(String url) throws Exception {
        return mockMvc.perform(get(url).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static String section(String html, String code) {
        int start = html.indexOf("id=\"tab-" + code + "\"");
        assertTrue(start >= 0, "нет секции вкладки " + code);
        int end = html.indexOf("</section>", start);
        assertTrue(end > start, "секция вкладки " + code + " не закрыта");
        return html.substring(start, end);
    }

    private static boolean sectionHidden(String html, String code) {
        int start = html.indexOf("id=\"tab-" + code + "\"");
        assertTrue(start >= 0, "нет секции вкладки " + code);
        int tagEnd = html.indexOf('>', start);
        return html.substring(start, tagEnd).contains("hidden");
    }

    private static int count(String html, String needle) {
        int found = 0;
        for (int at = html.indexOf(needle); at >= 0; at = html.indexOf(needle, at + needle.length()))
            found++;
        return found;
    }
}
