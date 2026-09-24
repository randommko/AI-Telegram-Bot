package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Authenticator;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String botToken = null;
        String dbUrl = null;
        String dbUser = null;
        String dbPassword = null;
        String gigaChatToken = null;
        String proxyHost = null;
        int proxyPort = 0;
        String proxyUser = null;
        String proxyPass = null;
        boolean proxyEnabled = false;

        try {
            ObjectMapper mapper = new ObjectMapper();
            java.io.InputStream is = Main.class.getClassLoader().getResourceAsStream("config.json");
            if (is == null) {
                logger.error("Ошибка: файл config.json не найден.");
                System.exit(1);
            }
            JsonNode root = mapper.readTree(is);
            String profileName = root.get("activeProfile").asText().toUpperCase();
            JsonNode profile = root.get("profiles").get(profileName);

            if (profile == null) {
                logger.error("Ошибка: профиль '{}' не найден.", profileName);
                System.exit(1);
            }

            botToken = profile.get("botToken").asText();
            dbUrl = profile.get("dbUrl").asText();
            dbUser = profile.get("dbUser").asText();
            dbPassword = profile.get("dbPassword").asText();
            gigaChatToken = profile.get("gigaChatToken").asText();

            JsonNode proxyNode = profile.get("proxy");
            proxyEnabled = proxyNode.get("enabled").asBoolean();
            proxyHost = proxyNode.get("host").asText();
            proxyPort = proxyNode.get("port").asInt();
            proxyUser = proxyNode.get("username").asText();
            proxyPass = proxyNode.get("password").asText();

            logger.info("Запущен профиль: {}", profileName);
            logger.info("Прокси: {}", proxyEnabled ? String.format("SOCKS5 %s:%d", proxyHost, proxyPort) : "отключено");

            if (botToken.isEmpty() || dbUrl.isEmpty() || dbUser.isEmpty() || dbPassword.isEmpty()) {
                logger.error("Ошибка: не заполнены параметры для профиля '{}'.", profileName);
                System.exit(1);
            }
        } catch (Exception e) {
            logger.error("Ошибка при чтении конфигурации: ", e);
            System.exit(1);
        }

        try {
            DataSourceConfig.initialize(dbUrl, dbUser, dbPassword);
        } catch (Exception e) {
            logger.error("Ошибка при подключении к БД: ", e);
        }

        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                attempt++;

                // Создаем кастомный HTTP-клиент с SOCKS5 прокси для Telegram API
                OkHttpClient httpClient;
                if (proxyEnabled && proxyHost != null && !proxyHost.isEmpty()) {
                    Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
                    
                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .proxy(proxy)
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                            .hostnameVerifier(new HostnameVerifier() {
                                @Override
                                public boolean verify(String hostname, SSLSession session) {
                                    return true;
                                }
                            });

                    if (proxyUser != null && !proxyUser.isEmpty()) {
                        final String user = proxyUser;
                        final String pass = proxyPass != null ? proxyPass : "";
                        builder.proxyAuthenticator((route, response) -> {
                            PasswordAuthentication auth = new PasswordAuthentication(user, pass.toCharArray());
                            if (auth == null) {
                                return null;
                            }
                            String credential = Credentials.basic(auth.getUserName(), auth.getPassword());
                            return response.request().newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build();
                        });
                        logger.info("SOCKS5 прокси с аутентификацией: {}:{}", proxyHost, proxyPort);
                    } else {
                        logger.info("SOCKS5 прокси без аутентификации: {}:{}", proxyHost, proxyPort);
                    }

                    httpClient = builder.build();
                } else {
                    httpClient = new OkHttpClient();
                }

                org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient telegramClient =
                        new org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient(httpClient);

                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class, telegramClient);
                botsApi.registerBot(new TelegramBot(botToken, gigaChatToken));

                logger.info("Бот успешно запущен на попытке №{}", attempt);
                break;
            } catch (TelegramApiException e) {
                logger.error("Ошибка при запуске бота на попытке №{}", attempt, e);

                if (attempt >= maxRetries) {
                    logger.error("Бот не запустился после {} попыток.", maxRetries);
                } else {
                    logger.info("Повторная попытка запуска...");
                }
            }
        }
    }
}
