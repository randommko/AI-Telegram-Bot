package org.example.core.aichat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorFormatTest {

    @Test
    void signUsesNickAndHumanName() {
        assertEquals("@nick (Ник)", AuthorFormat.sign(new MessageAuthor(1L, "@nick", "Ник")));
    }

    @Test
    void signAddsMissingAt() {
        assertEquals("@nick (Ник)", AuthorFormat.sign(new MessageAuthor(1L, "nick", "Ник")));
    }

    @Test
    void signNeverDoublesAt() {
        assertEquals("@nick", AuthorFormat.sign(new MessageAuthor(1L, "@@nick", null)));
    }

    @Test
    void signOmitsNameWhenItRepeatsNick() {
        assertEquals("@nick", AuthorFormat.sign(new MessageAuthor(1L, "@nick", "nick")));
    }

    @Test
    void signFallsBackToNameWithoutUsername() {
        assertEquals("@Иван", AuthorFormat.sign(new MessageAuthor(1L, null, "Иван")));
    }

    @Test
    void signIsEmptyWithoutAuthor() {
        assertEquals("", AuthorFormat.sign(MessageAuthor.none()));
        assertEquals("", AuthorFormat.sign(null));
    }

    @Test
    void renderPrefixesOnlyMessagesWithAuthor() {
        assertEquals("@nick (Ник): привет", AuthorFormat.render(new MessageAuthor(1L, "@nick", "Ник"), "привет"));
        assertEquals("ответ бота", AuthorFormat.render(MessageAuthor.none(), "ответ бота"));
    }
}
