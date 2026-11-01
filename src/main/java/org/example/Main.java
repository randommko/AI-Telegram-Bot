package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String botToken = resolveParameter(args, 0, "BOT_TOKEN");
        String dbUrl = resolveParameter(args, 1, "BOT_DB_URL");
        String dbUser = resolveParameter(args, 2, "BOT_DB_USER");
        String dbPassword = resolveParameter(args, 3, "BOT_DB_PASSWORD");
        String aiToken = resolveParameter(args, 4, "BOT_AI_KEY");

        if (botToken.isEmpty() || dbUrl.isEmpty() || dbUser.isEmpty() || dbPassword.isEmpty() || aiToken.isEmpty()) {
            logger.error("Ошибка: не заполнены обязательные параметры запуска. Передайте 5 аргументов "
                    + "(token dbUrl dbUser dbPassword aiKey) или переменные окружения "
                    + "BOT_TOKEN, BOT_DB_URL, BOT_DB_USER, BOT_DB_PASSWORD, BOT_AI_KEY.");
            System.exit(1);
        }

        DefaultBotOptions botOptions = configureProxy();

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

                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                TelegramBot bot = new TelegramBot(botToken, aiToken, botOptions);
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

    /**
     * Берёт параметр из аргумента командной строки, иначе из переменной окружения.
     * Аргументы приоритетнее, чтобы локальный запуск не зависел от переменных среды.
     */
    private static String resolveParameter(String[] args, int index, String envName) {
        if (args.length > index && !args[index].isBlank()) {
            return args[index];
        }
        String value = System.getenv(envName);
        return value == null ? "" : value;
    }

    /**
     * Настраивает прокси для Telegram API по переменным окружения BOT_PROXY_*.
     * Если BOT_PROXY_HOST не задан, возвращает настройки без прокси — прямое подключение.
     */
    private static DefaultBotOptions configureProxy() {
        String host = System.getenv("BOT_PROXY_HOST");
        if (host == null || host.isBlank()) {
            logger.info("Прокси для Telegram API не задан — прямое подключение");
            return new DefaultBotOptions();
        }

        String type = System.getenv("BOT_PROXY_TYPE");
        if (type == null || type.isBlank()) {
            type = "HTTP";
        }

        String portValue = System.getenv("BOT_PROXY_PORT");
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            logger.error("Ошибка: некорректный BOT_PROXY_PORT='{}', прокси отключён", portValue);
            return new DefaultBotOptions();
        }

        boolean socks = "SOCKS5".equalsIgnoreCase(type) || "SOCKS4".equalsIgnoreCase(type);

        DefaultBotOptions options = new DefaultBotOptions();
        if ("SOCKS5".equalsIgnoreCase(type)) {
            options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
        } else if ("SOCKS4".equalsIgnoreCase(type)) {
            options.setProxyType(DefaultBotOptions.ProxyType.SOCKS4);
        } else {
            options.setProxyType(DefaultBotOptions.ProxyType.HTTP);
        }
        options.setProxyHost(host);
        options.setProxyPort(port);

        // Системные свойства для java.net.http.HttpClient (DeepSeek, гороскоп)
        if (socks) {
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", String.valueOf(port));
        } else {
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", String.valueOf(port));
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", String.valueOf(port));
        }

        String user = System.getenv("BOT_PROXY_USER");
        if (user != null && !user.isBlank()) {
            final String proxyUser = user;
            final String proxyPass = System.getenv("BOT_PROXY_PASS") != null
                    ? System.getenv("BOT_PROXY_PASS")
                    : "";
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    return new java.net.PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                }
            });
            logger.info("Прокси {}:{} ({}) с аутентификацией", host, port, type);
        } else {
            logger.info("Прокси {}:{} ({}) без аутентификации", host, port, type);
        }

        return options;
    }
}
