package org.example.aichat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAddressParserTest {

    private static final List<String> ALIASES = List.of("юр", "юра", "юрец", "юрчик", "юрий");

    private final AiAddressParser parser = new AiAddressParser();

    @Test
    void everyAddressFormIsRecognized() {
        assertEquals(Optional.of("юра привет"), parser.question("юра привет", ALIASES));
        assertEquals(Optional.of("юрец привет"), parser.question("юрец привет", ALIASES));
        assertEquals(Optional.of("юрчик привет"), parser.question("юрчик привет", ALIASES));
        assertEquals(Optional.of("юрий привет"), parser.question("юрий привет", ALIASES));
        assertEquals(Optional.of("юр привет"), parser.question("юр привет", ALIASES));
    }

    @Test
    void addressIsRecognizedInAnyCase() {
        assertEquals(Optional.of("ЮРА как дела?"), parser.question("ЮРА как дела?", ALIASES));
        assertEquals(Optional.of("Юрец как дела?"), parser.question("Юрец как дела?", ALIASES));
        assertEquals(Optional.of("ЮрЧиК как дела?"), parser.question("ЮрЧиК как дела?", ALIASES));
    }

    @Test
    void addressInTheMiddleOrAtTheEndIsRecognized() {
        assertEquals(Optional.of("как дела, юра?"), parser.question("как дела, юра?", ALIASES));
        assertEquals(Optional.of("слушай юра привет"), parser.question("слушай юра привет", ALIASES));
        assertEquals(Optional.of("ну что, юрец?"), parser.question("ну что, юрец?", ALIASES));
    }

    @Test
    void questionKeepsTheOriginalText() {
        assertEquals(Optional.of("Юра, как дела?"), parser.question("Юра, как дела?", ALIASES));
        assertEquals(Optional.of("юра   ты тут"), parser.question("  юра   ты тут  ", ALIASES));
    }

    @Test
    void addressWithoutQuestionGivesEmptyQuestion() {
        assertEquals(Optional.of(""), parser.question("юр", ALIASES));
        assertEquals(Optional.of(""), parser.question("Юрий!", ALIASES));
        assertEquals(Optional.of(""), parser.question("Юра,   ", ALIASES));
        assertEquals(Optional.of(""), parser.question("юрец", ALIASES));
        assertEquals(Optional.of(""), parser.question("юра?", List.of("юра")));
    }

    @Test
    void wordsStartingLikeAnAddressAreNotAnAddress() {
        assertTrue(parser.question("юридический вопрос по договору", ALIASES).isEmpty());
        assertTrue(parser.question("юрта стоит в степи", ALIASES).isEmpty());
        assertTrue(parser.question("юркий парень", ALIASES).isEmpty());
    }

    @Test
    void wordContainingAnAddressIsNotAnAddress() {
        assertTrue(parser.question("плюрализм мнений", ALIASES).isEmpty());
        assertTrue(parser.question("неюра", ALIASES).isEmpty());
    }

    @Test
    void aliasesComeFromTheDatabase() {
        assertEquals(Optional.of("бот привет"), parser.question("бот привет", List.of("бот")));
        assertTrue(parser.question("юра привет", List.of("бот")).isEmpty());
    }

    @Test
    void plainTextWithoutAliasesIsNotAnAddress() {
        assertTrue(parser.question("привет всем", ALIASES).isEmpty());
        assertTrue(parser.question("", ALIASES).isEmpty());
        assertTrue(parser.question(null, ALIASES).isEmpty());
        assertTrue(parser.question("юра привет", List.of()).isEmpty());
        assertTrue(parser.question("юра привет", null).isEmpty());
    }
}
