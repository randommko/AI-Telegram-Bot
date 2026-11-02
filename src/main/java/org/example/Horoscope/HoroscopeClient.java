package org.example.Horoscope;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Текст гороскопа со страницы horo.mail.ru: ignio.com, который использовался раньше, перестал существовать.
 */
@Component
public class HoroscopeClient {
    private static final String URL_TEMPLATE = "https://horo.mail.ru/prediction/%s/%s/";
    private static final Pattern ARTICLE_BODY = Pattern.compile(
            "<main\\b[^>]*itemprop=\"articlebody\"[^>]*>(.*?)</main>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH = Pattern.compile("<p\\b[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public String fetch(String zodiacCode, String day) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_TEMPLATE.formatted(zodiacCode, day)))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос гороскопа прерван", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("horo.mail.ru вернул код " + response.statusCode());
        }

        String text = extractText(response.body());
        if (text.isBlank()) {
            throw new IOException("на странице нет текста гороскопа");
        }
        return text;
    }

    static String extractText(String html) {
        Matcher body = ARTICLE_BODY.matcher(html);
        if (!body.find()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        Matcher paragraph = PARAGRAPH.matcher(body.group(1));
        while (paragraph.find()) {
            String plain = unescape(TAG.matcher(paragraph.group(1)).replaceAll("")).trim();
            if (!plain.isEmpty()) {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(plain);
            }
        }
        return text.toString();
    }

    private static String unescape(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
