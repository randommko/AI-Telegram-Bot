package org.example.AiChat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MEMORY_ANALYSIS_MAX_TOKENS;
import static org.example.Settings.Settings.AI_MEMORY_ANALYSIS_MESSAGES;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMemoryExtractorTest {

    private static final ChatPersona CHAT = new ChatPersona(1L, 7);

    @Mock
    private PersonaRepository personaRepository;
    @Mock
    private MemoryRepository memoryRepository;
    @Mock
    private LlmClient llmClient;
    @Mock
    private SettingsService settings;

    private AiMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AiMemoryExtractor(personaRepository, memoryRepository,
                new MemoryExtractionParser(), llmClient, settings);

        lenient().when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MESSAGES)).thenReturn("50");
        lenient().when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MAX_TOKENS)).thenReturn("8000");
    }

    @Test
    void belowThresholdDoesNotCallModel() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(49);

        extractor.analyzeDueChats();

        verifyNoInteractions(llmClient);
        verify(memoryRepository, never()).apply(anyLong(), anyInt(), any(), anyLong(), anySet(), anyMap());
    }

    @Test
    void thresholdReachedAnalyzesAndSavesWatermark() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(50);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 50));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "Сообщение от: @nick: привет"),
                new MemoryRepository.MessageRow(61L, "Сообщение от: @ann: привет")));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of());
        when(memoryRepository.categories()).thenReturn(Set.of("хобби", "другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of("nick", 111L));
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[{\"category\":\"хобби\",\"content\":\"играет в шахматы\","
                        + "\"importance\":6,\"event_date\":null,\"people\":[\"nick\"]}],"
                        + "\"update\":[],\"delete\":[]}");

        extractor.analyzeDueChats();

        ArgumentCaptor<MemoryExtraction> extraction = ArgumentCaptor.forClass(MemoryExtraction.class);
        verify(memoryRepository).apply(eq(1L), eq(7), extraction.capture(), eq(61L), anySet(), anyMap());
        assertEquals(1, extraction.getValue().add().size());
        assertEquals("играет в шахматы", extraction.getValue().add().get(0).content());
    }

    @Test
    void messageLineUsesAuthorColumnsLikeHistory() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 1));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "привет", Instant.parse("2026-11-03T10:00:00Z"),
                        new MessageAuthor(111L, "@nick", "Ник"))));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of());
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[]}");

        extractor.analyzeChat(CHAT, 100);

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        verify(llmClient).sendRequestToAi(payload.capture(), any(Float.class), anyInt());
        String user = payload.getValue().get(1).get("content").asText();
        assertTrue(user.contains("] @nick (Ник): привет"), user);
    }

    @Test
    void returnsCountsOfAppliedChanges() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 2));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "первое"),
                new MemoryRepository.MessageRow(61L, "второе")));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of(
                new MemoryRecord(1, "друзья", "дружит с Ваней", 7, false, null, List.of(), "manual", Instant.EPOCH),
                new MemoryRecord(2, "семья", "день рождения", 10, true, null, List.of(), "manual", Instant.EPOCH),
                new MemoryRecord(3, "работа", "старая работа", 4, false, null, List.of(), "auto", Instant.EPOCH)));
        when(memoryRepository.categories()).thenReturn(Set.of("хобби", "другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[{\"category\":\"хобби\",\"content\":\"играет в шахматы\","
                        + "\"importance\":6,\"event_date\":null,\"people\":[]}],"
                        + "\"update\":[],\"delete\":[1,2,3]}");

        AiMemoryExtractor.AnalysisResult result = extractor.analyzeChat(CHAT, 100);

        assertEquals(2, result.messages());
        assertEquals(1, result.added());
        assertEquals(0, result.updated());
        assertEquals(1, result.deleted(), "удалить можно только запись auto");
        assertEquals(2, result.rejectedDeletes(), "ручная и ядровая записи защищены");
    }

    @Test
    void returnsZeroWhenThereAreNoNewMessages() throws Exception {
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 2));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of());

        AiMemoryExtractor.AnalysisResult result = extractor.analyzeChat(CHAT, 100);

        assertEquals(0, result.messages());
        verifyNoInteractions(llmClient);
    }

    @Test
    void analysisLimitsAreReadFromSettings() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MESSAGES)).thenReturn("20");
        when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MAX_TOKENS)).thenReturn("6000");
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 150));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(messages(150));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of());
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[]}");

        extractor.analyzeChat(CHAT, 300);

        ArgumentCaptor<ArrayNode> payload = ArgumentCaptor.forClass(ArrayNode.class);
        ArgumentCaptor<Integer> maxTokens = ArgumentCaptor.forClass(Integer.class);
        verify(llmClient).sendRequestToAi(payload.capture(), any(Float.class), maxTokens.capture());
        String sent = payload.getValue().get(1).get("content").asText();
        assertTrue(sent.contains("msg-130 от @nick"), "граница задаётся настройкой");
        assertFalse(sent.contains("msg-129 от @nick"));
        assertEquals(6000, maxTokens.getValue());
        verify(memoryRepository).apply(eq(1L), eq(7), any(), eq(1149L), anySet(), anyMap());
    }

    @Test
    void invalidAnalysisLimitsFallBackToDefault() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MESSAGES)).thenReturn("0");
        when(settings.getSettingValue(AI_MEMORY_ANALYSIS_MAX_TOKENS)).thenReturn("20000");
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 3));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(messages(3));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of());
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[]}");

        extractor.analyzeChat(CHAT, 300);

        ArgumentCaptor<Integer> maxTokens = ArgumentCaptor.forClass(Integer.class);
        verify(llmClient).sendRequestToAi(any(ArrayNode.class), any(Float.class), maxTokens.capture());
        assertEquals(8000, maxTokens.getValue(), "вне диапазона — берётся значение по умолчанию");
    }

    private static List<MemoryRepository.MessageRow> messages(int count) {
        List<MemoryRepository.MessageRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++)
            rows.add(new MemoryRepository.MessageRow(1000 + i, "msg-" + i + " от @nick"));
        return rows;
    }

    @Test
    void modelErrorLeavesMemoryAndWatermarkUntouched() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(50);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 50));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "Сообщение от: @nick: привет")));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt())).thenReturn(null);

        extractor.analyzeDueChats();

        verify(memoryRepository, never()).apply(anyLong(), anyInt(), any(), anyLong(), anySet(), anyMap());
    }

    @Test
    void coreRecordDeletionIsDropped() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(50);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 50));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "Сообщение от: @nick: привет")));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of(
                new MemoryRecord(5, "семья", "родился 1988-04-12", 10, true, LocalDate.of(1988, 4, 12), List.of()),
                new MemoryRecord(6, "хобби", "шахматы", 5, false, null, List.of(), "auto", Instant.EPOCH)));
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[{\"id\":5},{\"id\":6}]}");

        extractor.analyzeDueChats();

        ArgumentCaptor<MemoryExtraction> extraction = ArgumentCaptor.forClass(MemoryExtraction.class);
        verify(memoryRepository).apply(eq(1L), eq(7), extraction.capture(), eq(60L), anySet(), anyMap());
        assertEquals(List.of(6L), extraction.getValue().delete());
    }

    @Test
    void manualRecordDeletionIsDropped() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT)).thenReturn("промпт анализа");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(50);
        when(memoryRepository.window(1L)).thenReturn(new MemoryRepository.WindowInfo(11L, 50));
        when(memoryRepository.findNewMessages(1L, 10L)).thenReturn(List.of(
                new MemoryRepository.MessageRow(60L, "Сообщение от: @nick: привет")));
        when(memoryRepository.findByPersona(7)).thenReturn(List.of(
                new MemoryRecord(7, "друзья", "Алексей — это Лёха", 6, false, null, List.of(),
                        "manual", Instant.EPOCH)));
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(llmClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[{\"id\":7}]}");

        extractor.analyzeDueChats();

        ArgumentCaptor<MemoryExtraction> extraction = ArgumentCaptor.forClass(MemoryExtraction.class);
        verify(memoryRepository).apply(eq(1L), eq(7), extraction.capture(), eq(60L), anySet(), anyMap());
        assertTrue(extraction.getValue().delete().isEmpty(), "ручную запись анализ удалять не должен");
    }
}
