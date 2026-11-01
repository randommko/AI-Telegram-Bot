package org.example.PidorGame;

import com.vdurmont.emoji.EmojiParser;
import org.example.MessageSender;
import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PidorGame {
        private static final Logger logger = LoggerFactory.getLogger(PidorGame.class);
        private final PidorGameRepository repo;
        private final UsersService usersService;
        private final MessageSender sender;

        // Thread-safe map для флагов "в процессе" по чатам
        private static final ConcurrentHashMap<Long, AtomicBoolean> processingFlags = new ConcurrentHashMap<>();

        public PidorGame(PidorGameRepository repo, UsersService usersService, MessageSender sender) {
                this.repo = repo;
                this.usersService = usersService;
                this.sender = sender;
        }
        public void registerPlayer(Long chatID, Long userID) {
                String userName = usersService.getUserNameByID(userID);
                Integer numOfChanges = repo.registerPlayer(chatID, userID);
                if (numOfChanges != 0)
                        sender.sendMessage(chatID, "Игрок " + userName + " зарегистрирован!");
                else
                        sender.sendMessage(chatID, "Игрок " + userName + " был зарегистрирован ранее ");
        }
        public void sendPidorStats(Long chatID) {
                //String - имя пользователя, Integer - количество побед
                Map<String, Integer> stats = repo.getPidorStats(chatID);

                // Преобразуем Map в List<Entry> и сортируем по убыванию значений
                List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(stats.entrySet());
                sortedList.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

                // Если нужно, можно вернуть отсортированную карту
                Map<String, Integer> sortedMap = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : sortedList) {
                        sortedMap.put(entry.getKey(), entry.getValue());
                }
                StringBuilder statsMessage = new StringBuilder(EmojiParser.parseToUnicode(":rainbow_flag: Статистика викторины:\n"));
                sortedMap.forEach((userName, score) ->
                                statsMessage.append(userName.startsWith("@") ? EmojiParser.parseToUnicode(":rainbow_flag:") + userName.substring(1) : EmojiParser.parseToUnicode(":rainbow_flag:") + userName)
                                .append(": ").append(score).append(" побед\n")
                );
                sender.sendMessage(chatID, statsMessage.toString());
        }
        public void startPidorGame(Long chatID, Long userID) {
            logger.info("Запущена игра пидорвикторина в чате: {}", chatID);

                // Получаем или создаем AtomicBoolean для этого чата
                AtomicBoolean processing = processingFlags.computeIfAbsent(chatID, k -> new AtomicBoolean(false));

                // Пытаемся атомарно захватить "lock" - если уже true, то процесс идет
                if (!processing.compareAndSet(false, true)) {
                    logger.info("Поиск пидора дня уже запущен в чате {}", chatID);
                        sender.sendMessage(chatID, EmojiParser.parseToUnicode(":rainbow_flag: Поиск пидора дня уже запущен! Подождите завершения."));
                        return;
                }

                try {
                        Long winnerID = repo.getTodayWinner(chatID);

                        if (!repo.getPidorGamePlayers(chatID).contains(userID) && winnerID == null) {
                                logger.info("Игру пытается запустить не зарегистрированный игрок и пидор дня не найден");
                                sender.sendMessage(chatID, """
                                Сегодня пидора дня еще не выбирали.
                                Игру может начать только зарегистрированный игрок.
                                Зарегистрируйтесь командой /pidor_reg""");
                                return;
                        }

                        if (winnerID != null) {
                                logger.info("Найден пидор на текущую дату. Информируем пользователя");
                                sender.sendMessage(chatID,
                                        EmojiParser.parseToUnicode((":rainbow_flag: Сегодня пидора уже выбрали. Пидор дня: " + usersService.getUserNameByID(winnerID))));
                                return;
                        }

                        Set<Long> chatPlayers = repo.getPidorGamePlayers(chatID);
                        if (chatPlayers.isEmpty()) {
                            logger.info("Количество игроков: {} в чате {} Игра не началась. Нет зарегистрированных игроков.", 0, chatID);
                                sender.sendMessage(chatID, "Нет зарегистрированных игроков.");
                                return;
                        }

                        if (chatPlayers.size() < 2) {
                            logger.info("Количество игроков: {} в чате {} Игра не началась, недостаточно игроков", chatPlayers.size(), chatID);
                                sender.sendMessage(chatID, "Для игры необходимо хотя бы два игрока. Зарегистрируйтесь командой /pidor_reg");
                                return;
                        }

                        List<String> responses = repo.getRandomResponses();
                        try {
                                for (String response : responses) {
                                        sender.sendMessage(chatID, response);
                                        Thread.sleep(2000);
                                }
                        } catch (Exception e) {
                                sender.sendMessage(chatID, "Ищем пидора запасным вариантом...");
                                logger.error("Произошла ошибка при получении из БД списка сообщений: ", e);
                        }
                        winnerID = new ArrayList<>(chatPlayers).get(new Random().nextInt(chatPlayers.size()));
                        repo.setPidorWinner(chatID, winnerID);
                        sender.sendMessage(chatID, EmojiParser.parseToUnicode(":rainbow_flag:") +
                                " " + repo.getWinnerResponse() + usersService.getUserNameByID(winnerID) + "!");

                } finally {
                        // Всегда освобождаем флаг
                        processing.set(false);
                }

        }
}
