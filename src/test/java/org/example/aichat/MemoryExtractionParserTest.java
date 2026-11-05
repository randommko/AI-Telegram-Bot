package org.example.aichat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryExtractionParserTest {

    private final MemoryExtractionParser parser = new MemoryExtractionParser();

    @Test
    void parsesAddUpdateAndDelete() {
        String json = """
                {"add":[{"category":"хобби","content":"играет в шахматы по средам","importance":6,
                         "event_date":"2025-11-01","people":["@nick","@ann"]}],
                 "update":[{"id":12,"category":"работа","content":"сменил работу","importance":8,
                            "event_date":null,"people":[]}],
                 "delete":[{"id":13}]}
                """;

        MemoryExtraction extraction = parser.parse(json);

        assertEquals(1, extraction.add().size());
        MemoryDraft added = extraction.add().get(0);
        assertEquals("хобби", added.category());
        assertEquals("играет в шахматы по средам", added.content());
        assertEquals(6, added.importance());
        assertEquals(LocalDate.of(2025, 11, 1), added.eventDate());
        assertEquals(List.of("nick", "ann"), added.people());

        assertEquals(12L, extraction.update().get(0).id());
        assertNull(extraction.update().get(0).eventDate());
        assertEquals(List.of(13L), extraction.delete());
    }

    @Test
    void stripsMarkdownFences() {
        String json = """
                ```json
                {"add":[{"category":"семья","content":"есть брат","importance":7,"event_date":null,"people":[]}],
                 "update":[],"delete":[]}
                ```
                """;

        MemoryExtraction extraction = parser.parse(json);

        assertEquals(1, extraction.add().size());
        assertEquals(7, extraction.add().get(0).importance());
    }

    @Test
    void importanceIsClamped() {
        String json = """
                {"add":[{"category":"хобби","content":"первое","importance":99,"event_date":null,"people":[]},
                        {"category":"хобби","content":"второе","importance":-3,"event_date":null,"people":[]}],
                 "update":[],"delete":[]}
                """;

        MemoryExtraction extraction = parser.parse(json);

        assertEquals(10, extraction.add().get(0).importance());
        assertEquals(1, extraction.add().get(1).importance());
    }

    @Test
    void brokenDateBecomesNull() {
        String json = """
                {"add":[{"category":"события","content":"был на свадьбе","importance":5,
                         "event_date":"12 марта","people":[]}],
                 "update":[],"delete":[]}
                """;

        assertNull(parser.parse(json).add().get(0).eventDate());
    }

    @Test
    void operationWithoutContentIsSkipped() {
        String json = """
                {"add":[{"category":"хобби","content":"  ","importance":5,"event_date":null,"people":[]}],
                 "update":[],"delete":[]}
                """;

        assertTrue(parser.parse(json).add().isEmpty());
    }

    @Test
    void numericDeleteIdsAreAccepted() {
        String json = "{\"add\":[],\"update\":[],\"delete\":[7,\"8\"]}";

        assertEquals(List.of(7L, 8L), parser.parse(json).delete());
    }

    @Test
    void invalidJsonIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("не json"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void missingOperationArrayIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"add\":[],\"update\":[]}"));
    }
}
