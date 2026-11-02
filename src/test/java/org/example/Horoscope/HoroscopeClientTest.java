package org.example.Horoscope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HoroscopeClientTest {

    @Test
    void extractsParagraphsFromArticleBody() {
        String html = "<html><body>"
                + "<main itemProp=\"articleBody\" data-qa=\"Main\" class=\"x\">"
                + "<div article-item-type=\"html\"><p>Первый &laquo;абзац&raquo;.</p>"
                + "<p>Второй&nbsp;абзац &amp; ещё.</p></div></main>"
                + "<p>Текст вне статьи не должен попасть в ответ</p>"
                + "</body></html>";

        assertThat(HoroscopeClient.extractText(html))
                .isEqualTo("Первый «абзац».\n\nВторой абзац & ещё.");
    }

    @Test
    void returnsEmptyWhenArticleBodyIsAbsent() {
        assertThat(HoroscopeClient.extractText("<html><body><p>нет статьи</p></body></html>")).isEmpty();
    }

    @Test
    void parsesRealMailRuMarkup() throws Exception {
        String html = new String(getClass().getResourceAsStream("/horoscope/aries-main.html").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        String text = HoroscopeClient.extractText(html);

        assertThat(text).startsWith("Спокойное начало рабочей недели");
        assertThat(text.split("\n\n")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(text).doesNotContain("<p").doesNotContain("&nbsp;");
    }
}
