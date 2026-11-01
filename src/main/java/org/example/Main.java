package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 5) {
            logger.error("Ошибка: ожидается 5 аргументов: token dbUrl dbUser dbPassword aiKey");
            System.exit(1);
        }

        String botToken = args[0];
        String dbUrl = args[1];
        String dbUser = args[2];
        String dbPassword = args[3];
        String aiToken = args[4];

        if (botToken.isEmpty() || dbUrl.isEmpty() || dbUser.isEmpty() || dbPassword.isEmpty() || aiToken.isEmpty()) {
            logger.error("Ошибка: не заполнены обязательные параметры запуска.");
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

                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                TelegramBot bot = new TelegramBot(botToken, aiToken);
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
