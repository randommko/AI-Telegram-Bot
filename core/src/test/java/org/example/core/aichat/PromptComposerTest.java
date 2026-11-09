package org.example.core.aichat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.example.core.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_PROMPT_EMOTIONS_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_MEMORY_CORE_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_PEOPLE_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_TECHNICAL;
import static org.example.core.settings.Settings.AI_PROMPT_TIME_ZONE;
import static org.example.core.settings.Settings.USER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptComposerTest {

    private static final long CHAT_ID = 7L;
    private static final int PERSONA_ID = 3;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-03T22:20:00Z"), ZoneId.of("UTC"));

    @Mock
    private SettingsService settings;
    @Mock
    private PersonaService personaService;
    @Mock
    private EmotionService emotionService;
    @Mock
    private MemoryRepository memoryRepository;
    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private MemoryDecay memoryDecay;
    @Mock
    private PersonaGrudgeRepository grudgeRepository;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PromptComposer.class);

    private PromptComposer composer;

    @BeforeEach
    void setUp() {
        logger.addAppender(logs);
        logs.start();
        when(memoryDecay.daysPerPoint()).thenReturn(3);
        when(memoryDecay.minImportance()).thenReturn(1);
        when(memoryDecay.effective(any(MemoryRecord.class), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> ((MemoryRecord) invocation.getArgument(0)).importance());
        composer = new PromptComposer(settings, personaService, emotionService, memoryRepository,
                new MemoryPromptBuilder(memoryDecay), aiChatRepository, grudgeRepository, memoryDecay, CLOCK);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void sectionsFollowFixedOrder() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn("- Гнев 8/10: орёшь");
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of());
        when(memoryRepository.findTopRegular(PERSONA_ID, PromptComposer.DEFAULT_MEMORY_PROMPT_SIZE))
                .thenReturn(List.of(new MemoryRecord(2, "хобби", "шахматы", 6, false, null, List.of())));
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(111L, "@nick", "Ник", 1, 100)));

        List<PromptSection> order = composer.compose(CHAT_ID).sections().stream()
                .map(PromptComposer.Section::spec)
                .toList();

        assertEquals(List.of(
                PromptSection.TECHNICAL,
                PromptSection.PERSONA,
                PromptSection.EMOTIONS,
                PromptSection.MEMORY,
                PromptSection.PEOPLE,
                PromptSection.TIME,
                PromptSection.ANSWER_FORMAT), order);
    }

    @Test
    void emptySectionIsSkippedTogetherWithHeading() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());

        String prompt = composer.compose(CHAT_ID).text();

        assertFalse(prompt.contains("## Обиды"), prompt);
        assertFalse(prompt.contains("## Что ты помнишь"), prompt);
        assertFalse(prompt.contains("## Твоё состояние сейчас"), prompt);
        assertFalse(prompt.contains("## Кто в чате"), prompt);
        assertTrue(prompt.contains("## Сейчас"), prompt);
    }

    @Test
    void peopleListShowsMessageCountsAndCurrentAuthor() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(1L, "@old", "Старый", 34, 100),
                new AiChatRepository.AuthorStat(2L, "@mid", "Средний", 1, 90)));

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertTrue(people.contains("- @old (Старый) — 34 сообщения — сейчас пишет"), people);
        assertTrue(people.contains("- @mid (Средний) — 1 сообщение"), people);
        assertFalse(people.contains("@mid (Средний) — 1 сообщение — сейчас пишет"), people);
    }

    @Test
    void peopleListShowsOnlyActiveGrudge() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(1L, "@old", "Старый", 5, 100),
                new AiChatRepository.AuthorStat(2L, "@mid", "Средний", 3, 90)));
        when(grudgeRepository.findForChat(CHAT_ID)).thenReturn(List.of(
                new PersonaGrudgeRepository.GrudgeRow(1L, 4, CLOCK.instant(), "@old", "Старый"),
                new PersonaGrudgeRepository.GrudgeRow(2L, 0, CLOCK.instant(), "@mid", "Средний")));

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertTrue(people.contains("@old (Старый) — 5 сообщений, обида 4"), people);
        assertFalse(people.contains("@mid (Средний) — 3 сообщения, обида"), people);
    }

    @Test
    void personWithoutNameGoesWithoutEmptyBrackets() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(5L, "@nick", null, 2, 100)));

        String people = section(composer.compose(CHAT_ID), PromptSection.PEOPLE);

        assertTrue(people.contains("- @nick — 2 сообщения — сейчас пишет"), people);
        assertFalse(people.contains("()"), people);
    }

    @Test
    void timeUsesZoneFromSetting() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TIME_ZONE)).thenReturn("Europe/Moscow");

        String time = section(composer.compose(CHAT_ID), PromptSection.TIME);

        assertTrue(time.contains("4 ноября 2026"), time);
        assertTrue(time.contains("01:20"), time);
        assertTrue(time.contains("Europe/Moscow"), time);
        assertTrue(time.contains("среда"), time);
    }

    @Test
    void invalidZoneFallsBackToMoscow() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TIME_ZONE)).thenReturn("Марс/Олимп");

        String time = section(composer.compose(CHAT_ID), PromptSection.TIME);

        assertTrue(time.contains("Europe/Moscow"), time);
    }

    @Test
    void lengthPlaceholderIsFilledInTechnicalAndFormatSections() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());

        String prompt = composer.compose(CHAT_ID).text();

        assertFalse(prompt.contains("{maxLength}"), prompt);
        assertTrue(prompt.contains("4000"), prompt);
    }

    @Test
    void memoryHeadersComeFromSettings() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "родился", 10, true, LocalDate.of(1988, 4, 12), List.of())));
        when(settings.getSettingValue(AI_PROMPT_MEMORY_CORE_HEADER)).thenReturn("Моё ядро:");

        String memory = section(composer.compose(CHAT_ID), PromptSection.MEMORY);

        assertTrue(memory.startsWith("Моё ядро:"), memory);
        assertTrue(memory.contains("[семья] родился"), memory);
    }

    @Test
    void emotionHeaderComesFromSettings() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn("- Гнев 8/10: орёшь");
        when(settings.getSettingValue(AI_PROMPT_EMOTIONS_HEADER)).thenReturn("Настроение:");

        String emotions = section(composer.compose(CHAT_ID), PromptSection.EMOTIONS);

        assertTrue(emotions.startsWith("Настроение:"), emotions);
    }

    @Test
    void calmStateStaysOneLineWithoutHeader() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn(EmotionService.CALM_LINE);

        String emotions = section(composer.compose(CHAT_ID), PromptSection.EMOTIONS);

        assertEquals(EmotionService.CALM_LINE, emotions);
    }

    @Test
    void shownEmotionSectionIsTheSameTextTheModelGets() {
        withPersona();

        for (EmotionState state : List.of(angryState(), calmState(), sixDeviationsState())) {
            when(emotionService.bodyFor(PERSONA_ID, false)).thenReturn(EmotionService.bodyText(state));

            String shown = composer.emotionsSection(state);

            assertTrue(shown.startsWith(PromptSection.EMOTIONS.heading() + "\n"), shown);
            assertTrue(composer.compose(CHAT_ID).text().contains(shown), shown);
        }
    }

    @Test
    void calmEmotionSectionIsOneLineUnderHeading() {
        assertEquals(PromptSection.EMOTIONS.heading() + "\n" + EmotionService.CALM_LINE,
                composer.emotionsSection(calmState()));
    }

    @Test
    void missingStateGivesNoEmotionSection() {
        assertEquals("", composer.emotionsSection(null));
    }

    @Test
    void previewReadsEmotionsWithoutEnsuringRows() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.of(
                new Persona(PERSONA_ID, "Юра", "Ты Юра")));
        when(personaService.resolvePrompt(CHAT_ID)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты Юра", "persona:Юра")));
        when(emotionService.bodyFor(PERSONA_ID, true)).thenReturn("- Гнев 8/10: орёшь");

        composer.compose(CHAT_ID, true);

        verify(emotionService).bodyFor(PERSONA_ID, true);
        verify(emotionService, never()).bodyFor(PERSONA_ID, false);
    }

    @Test
    void blankSettingFallsBackToDefault() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_TECHNICAL)).thenReturn("   ");

        String prompt = composer.compose(CHAT_ID).text();

        assertTrue(prompt.contains("Ты участник этого чата"), prompt);
    }

    @Test
    void longTechnicalPartGoesToPromptWhole() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        String longTechnical = "Правило поведения. ".repeat(60).strip();
        when(settings.getSettingValue(AI_PROMPT_TECHNICAL)).thenReturn(longTechnical);

        String technical = section(composer.compose(CHAT_ID), PromptSection.TECHNICAL);

        assertTrue(longTechnical.length() > PromptSection.TECHNICAL.budget(), "текст длиннее рекомендуемой длины");
        assertEquals(longTechnical, technical);
    }

    @Test
    void settingsChangeIsVisibleBetweenCalls() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());
        when(settings.getSettingValue(AI_PROMPT_PEOPLE_HEADER))
                .thenReturn("Первые люди:")
                .thenReturn("Вторые люди:");
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(1L, "@nick", "Ник", 1, 100)));

        String first = composer.compose(CHAT_ID).text();
        String second = composer.compose(CHAT_ID).text();

        assertTrue(first.contains("Первые люди:"), first);
        assertTrue(second.contains("Вторые люди:"), second);
    }

    @Test
    void memorySectionUsesPlanSelection() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "ядровая запись", 10, true, null, List.of())));
        when(memoryRepository.findByIds(PERSONA_ID, List.of(3)))
                .thenReturn(List.of(memory(3, "море")));

        String memory = section(composer.compose(CHAT_ID, List.of(3)), PromptSection.MEMORY);

        assertTrue(memory.contains("море"), memory);
        assertFalse(memory.contains("шахматы"), memory);
        assertTrue(memory.contains("ядровая запись"), "ядро не вытесняется выбором");
        verify(memoryRepository, never()).findTopRegular(PERSONA_ID, PromptComposer.DEFAULT_MEMORY_PROMPT_SIZE);
    }

    @Test
    void emptyOrUnknownSelectionFallsBackToAllRegularMemories() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of());
        when(memoryRepository.findByIds(PERSONA_ID, List.of(999))).thenReturn(List.of());
        when(memoryRepository.findTopRegular(PERSONA_ID, PromptComposer.DEFAULT_MEMORY_PROMPT_SIZE))
                .thenReturn(List.of(memory(2, "шахматы"), memory(3, "море")));

        String empty = section(composer.compose(CHAT_ID, List.of()), PromptSection.MEMORY);
        String unknown = section(composer.compose(CHAT_ID, List.of(999)), PromptSection.MEMORY);

        assertTrue(empty.contains("шахматы") && empty.contains("море"), empty);
        assertTrue(unknown.contains("шахматы") && unknown.contains("море"), unknown);
    }

    @Test
    void selectedMemoryOutsideTopNIsIncluded() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of());
        when(memoryRepository.findTopRegular(PERSONA_ID, PromptComposer.DEFAULT_MEMORY_PROMPT_SIZE))
                .thenReturn(List.of(memory(2, "шахматы")));
        when(memoryRepository.findByIds(PERSONA_ID, List.of(40)))
                .thenReturn(List.of(memory(40, "сороковое воспоминание")));

        String memory = section(composer.compose(CHAT_ID, List.of(40)), PromptSection.MEMORY);

        assertTrue(memory.contains("сороковое воспоминание"), memory);
        assertFalse(memory.contains("шахматы"), "топ-N не подмешивается к выбору");
    }

    @Test
    void selectedCoreIsNotDuplicated() {
        withPersona();
        MemoryRecord coreMemory = new MemoryRecord(1, "семья", "ядровая запись", 10, true, null, List.of());
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(coreMemory));
        when(memoryRepository.findByIds(PERSONA_ID, List.of(1))).thenReturn(List.of(coreMemory));

        String memory = section(composer.compose(CHAT_ID, List.of(1)), PromptSection.MEMORY);

        assertEquals(1, memory.lines().filter(line -> line.contains("ядровая запись")).count(), memory);
    }

    @Test
    void memoryPoolTakesEventsOfAuthorAndTenPerCategory() {
        withPersona();
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(
                new MemoryRecord(1, "семья", "ядро", 10, true, null, List.of())));
        when(memoryRepository.findEvents(PERSONA_ID, 222L)).thenReturn(List.of(event(2, "с автором")));
        when(memoryRepository.findEvents(PERSONA_ID, null)).thenReturn(List.of(event(3, "чужое")));
        when(memoryRepository.findRegular(PERSONA_ID)).thenReturn(List.of(memory(4, "шахматы")));

        PromptComposer.MemoryPool pool = composer.memoryPool(CHAT_ID, 222L);

        assertEquals(1, pool.core().size());
        assertEquals(List.of(2, 4), pool.regular().stream().map(MemoryRecord::id).toList());
    }

    @Test
    void poolKeepsTenPerCategory() {
        withPersona();
        List<MemoryRecord> regular = new ArrayList<>();
        for (int i = 1; i <= 12; i++)
            regular.add(new MemoryRecord(i, "друзья", "друг " + i, 5, false, null, List.of()));
        for (int i = 1; i <= 3; i++)
            regular.add(new MemoryRecord(100 + i, "хобби", "хобби " + i, 5, false, null, List.of()));
        when(memoryRepository.findRegular(PERSONA_ID)).thenReturn(regular);

        PromptComposer.MemoryPool pool = composer.memoryPool(CHAT_ID, null);

        assertEquals(13, pool.regular().size());
        assertEquals(10, pool.regular().stream()
                .filter(memory -> "друзья".equals(memory.category())).count());
    }

    @Test
    void poolFallsBackToCommonEventsForNewParticipant() {
        withPersona();
        when(memoryRepository.findEvents(PERSONA_ID, 777L)).thenReturn(List.of());
        when(memoryRepository.findEvents(PERSONA_ID, null)).thenReturn(List.of(event(9, "общее")));

        PromptComposer.MemoryPool pool = composer.memoryPool(CHAT_ID, 777L);

        assertEquals(9, pool.regular().get(0).id());
    }

    @Test
    void poolRanksByEffectiveImportanceNotBase() {
        withPersona();
        MemoryRecord stale = event(1, "старое");
        MemoryRecord fresh = event(2, "свежее");
        when(memoryRepository.findEvents(PERSONA_ID, null)).thenReturn(List.of(stale, fresh));
        when(memoryDecay.effective(eq(stale), anyInt(), anyInt(), any())).thenReturn(2);
        when(memoryDecay.effective(eq(fresh), anyInt(), anyInt(), any())).thenReturn(9);

        PromptComposer.MemoryPool pool = composer.memoryPool(CHAT_ID, null);

        assertEquals(List.of(2, 1), pool.regular().stream().map(MemoryRecord::id).toList());
    }

    @Test
    void planContextOmitsPersonaAndAnswerFormat() {
        withPersona();
        when(emotionService.bodyFor(PERSONA_ID, true)).thenReturn("- Гнев 8/10: орёшь");
        when(memoryRepository.findCore(PERSONA_ID)).thenReturn(List.of(memory(1, "ядро")));
        when(aiChatRepository.findAuthorStats(CHAT_ID, PromptComposer.MAX_PEOPLE_LINES)).thenReturn(List.of(
                new AiChatRepository.AuthorStat(1L, "@nick", "Ник", 1, 100)));

        PromptComposer.ComposedPrompt context = composer.composeForPlan(CHAT_ID);

        List<PromptSection> keys = context.sections().stream()
                .map(PromptComposer.Section::spec)
                .toList();
        assertEquals(List.of(PromptSection.EMOTIONS,
                PromptSection.PEOPLE, PromptSection.TIME), keys);
        assertFalse(context.text().contains("Ты Юра"), "характер в план не попадает");
        assertFalse(context.text().contains("Ты участник этого чата"), "техника в план не попадает");
        assertFalse(context.text().contains("## Как отвечать"), context.text());
    }

    @Test
    void promptLogNamesTheChatsPersona() {
        withPersona();

        composer.compose(CHAT_ID);

        assertTrue(promptLog().contains("персона Юра"), promptLog());
    }

    @Test
    void promptLogSaysNoPersonaForChatWithoutIt() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.empty());

        composer.compose(CHAT_ID);

        assertTrue(promptLog().contains("персона без персоны"), promptLog());
    }

    private static EmotionState angryState() {
        return new EmotionState(List.of(
                new EmotionState.Line("гнев", "Гнев", 2, 6, 4, "Ты злишься")),
                3, "Агрессия", "Давит");
    }

    private static EmotionState calmState() {
        return new EmotionState(List.of(
                new EmotionState.Line("гнев", "Гнев", 2, 2, 0, "Спокойный тон")),
                0, "Агрессия", "Спокойный тон");
    }

    private static EmotionState sixDeviationsState() {
        List<EmotionState.Line> lines = new ArrayList<>();
        String[] codes = {"гнев", "радость", "печаль", "страх", "зависть", "стыд"};
        for (int i = 0; i < codes.length; i++)
            lines.add(new EmotionState.Line(codes[i], "Эмоция " + i, 1, 3 + i, 2 + i, "Фраза " + i));
        return new EmotionState(lines, 0, "Агрессия", "Спокойный тон");
    }

    private String promptLog() {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Системный промпт чата"))
                .findFirst()
                .orElse("");
    }

    private static MemoryRecord memory(int id, String content) {
        return new MemoryRecord(id, "другое", content, 5, false, null, List.of());
    }

    private static MemoryRecord event(int id, String content) {
        return new MemoryRecord(id, MemoryRepository.EVENT_CATEGORY, content, 5, false,
                LocalDate.of(2026, 11, 1), List.of());
    }

    private void withPersona() {
        when(personaService.personaForChat(CHAT_ID)).thenReturn(Optional.of(
                new Persona(PERSONA_ID, "Юра", "Ты Юра")));
        when(personaService.resolvePrompt(CHAT_ID)).thenReturn(Optional.of(
                new PersonaService.ResolvedPrompt("Ты Юра", "persona:Юра")));
    }

    private static String section(PromptComposer.ComposedPrompt prompt, PromptSection spec) {
        return prompt.sections().stream()
                .filter(section -> section.spec() == spec)
                .map(PromptComposer.Section::body)
                .findFirst()
                .orElse("");
    }

    private static AiMessage userMessage(String text, Long userId, String nick, String name) {
        return new AiMessage(USER_ROLE, text, new MessageAuthor(userId, nick, name));
    }
}
