package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

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

        // Настраиваем прокси для Telegram API через системные свойства
        if (proxyEnabled && proxyHost != null && !proxyHost.isEmpty()) {
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            if (proxyUser != null && !proxyUser.isEmpty()) {
                final String user = proxyUser;
                final String pass = proxyPass != null ? proxyPass : "";
                java.net.Authenticator.setDefault(new java.net.Authenticator() {
                    @Override
                    protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        return new java.net.PasswordAuthentication(user, pass.toCharArray());
                    }
                });
                logger.info("Прокси с аутентификацией: {}:{}", proxyHost, proxyPort);
            } else {
                logger.info("Прокси без аутентификации: {}:{}", proxyHost, proxyPort);
            }
        } else {
            logger.info("Прокси для Telegram API отключён");
        }

        org.telegram.telegrambots.bots.DefaultBotOptions options = new org.telegram.telegrambots.bots.DefaultBotOptions();
        options.setProxyType(org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType.SOCKS5);
        options.setProxyHost(proxyHost);
        options.setProxyPort(proxyPort);

        while (attempt < maxRetries) {
            try {
                attempt++;

                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                TelegramBot bot = new TelegramBot(botToken, gigaChatToken, options);
                botsApi.registerBot(bot);

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
