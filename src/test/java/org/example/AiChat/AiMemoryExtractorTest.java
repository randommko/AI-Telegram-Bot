package org.example.AiChat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.Settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
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
    private DeepSeekClient deepSeekClient;
    @Mock
    private SettingsService settings;

    private AiMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AiMemoryExtractor(personaRepository, memoryRepository,
                new MemoryExtractionParser(), deepSeekClient, settings);
    }

    @Test
    void belowThresholdDoesNotCallModel() throws Exception {
        when(settings.getSettingValue(AI_MEMORY_ANALYZE_EVERY)).thenReturn("50");
        when(settings.getSettingValue(AI_HISTORY_SIZE)).thenReturn("100");
        when(personaRepository.findAssignedChats()).thenReturn(List.of(CHAT));
        when(memoryRepository.watermark(1L)).thenReturn(10L);
        when(memoryRepository.countNewMessages(1L, 10L)).thenReturn(49);

        extractor.analyzeDueChats();

        verifyNoInteractions(deepSeekClient);
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
        when(deepSeekClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
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
        when(deepSeekClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt())).thenReturn(null);

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
                new MemoryRecord(6, "хобби", "шахматы", 5, false, null, List.of())));
        when(memoryRepository.categories()).thenReturn(Set.of("другое"));
        when(memoryRepository.userIds(any())).thenReturn(Map.of());
        when(deepSeekClient.sendRequestToAi(any(ArrayNode.class), any(Float.class), anyInt()))
                .thenReturn("{\"add\":[],\"update\":[],\"delete\":[{\"id\":5},{\"id\":6}]}");

        extractor.analyzeDueChats();

        ArgumentCaptor<MemoryExtraction> extraction = ArgumentCaptor.forClass(MemoryExtraction.class);
        verify(memoryRepository).apply(eq(1L), eq(7), extraction.capture(), eq(60L), anySet(), anyMap());
        assertEquals(List.of(6L), extraction.getValue().delete());
    }
}
