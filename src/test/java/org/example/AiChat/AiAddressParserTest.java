package org.example.AiChat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAddressParserTest {

    private final AiAddressParser parser = new AiAddressParser();

    @Test
    void everyAddressFormIsRecognized() {
        assertEquals(Optional.of("привет"), parser.question("юра привет"));
        assertEquals(Optional.of("привет"), parser.question("юрец привет"));
        assertEquals(Optional.of("привет"), parser.question("юрчик привет"));
        assertEquals(Optional.of("привет"), parser.question("юрий привет"));
        assertEquals(Optional.of("привет"), parser.question("юр привет"));
    }

    @Test
    void addressIsRecognizedInAnyCase() {
        assertEquals(Optional.of("как дела?"), parser.question("ЮРА как дела?"));
        assertEquals(Optional.of("как дела?"), parser.question("Юрец как дела?"));
        assertEquals(Optional.of("как дела?"), parser.question("ЮрЧиК как дела?"));
    }

    @Test
    void punctuationAfterAddressIsDropped() {
        assertEquals(Optional.of("как дела?"), parser.question("Юра, как дела?"));
        assertEquals(Optional.of("что нового"), parser.question("Юрий! что нового"));
        assertEquals(Optional.of("ну?"), parser.question("юр... ну?"));
        assertEquals(Optional.of("ты тут"), parser.question("  юра   ты тут  "));
    }

    @Test
    void addressWithoutQuestionGivesEmptyQuestion() {
        assertEquals(Optional.of(""), parser.question("юр"));
        assertEquals(Optional.of(""), parser.question("Юрий!"));
        assertEquals(Optional.of(""), parser.question("Юра,   "));
        assertEquals(Optional.of(""), parser.question("юрец"));
    }

    @Test
    void wordsStartingLikeAnAddressAreNotAnAddress() {
        assertTrue(parser.question("юридический вопрос по договору").isEmpty());
        assertTrue(parser.question("юрта стоит в степи").isEmpty());
        assertTrue(parser.question("юркий парень").isEmpty());
    }

    @Test
    void addressInTheMiddleIsNotAnAddress() {
        assertTrue(parser.question("как дела, юра?").isEmpty());
        assertTrue(parser.question("слушай юра привет").isEmpty());
    }

    @Test
    void plainTextIsNotAnAddress() {
        assertTrue(parser.question("привет всем").isEmpty());
        assertTrue(parser.question("").isEmpty());
        assertTrue(parser.question(null).isEmpty());
    }
}
