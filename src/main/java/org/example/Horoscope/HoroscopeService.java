package org.example.Horoscope;

import org.example.MessageSender;
import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HoroscopeService {
    private static final Map<String, String> zodiacMap;  //key - код знака зодиака, value - название знака зодиака
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String UNAVAILABLE_MESSAGE = "Гороскоп сейчас недоступен, попробуй позже.";

    private final UsersService usersService;
    private final MessageSender sender;
    private final HoroscopeClient horoscopeClient;

    // Текст на знак+день меняется раз в сутки, поэтому страницу не дёргаем на каждое нажатие.
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile LocalDate cacheDate = LocalDate.now();

    static {
        zodiacMap = new HashMap<>();
        zodiacMap.put("aries", "овен");
        zodiacMap.put("taurus", "телец");
        zodiacMap.put("gemini", "близнецы");
        zodiacMap.put("cancer", "рак");
        zodiacMap.put("leo", "лев");
        zodiacMap.put("virgo", "дева");
        zodiacMap.put("libra", "весы");
        zodiacMap.put("scorpio", "скорпион");
        zodiacMap.put("sagittarius", "стрелец");
        zodiacMap.put("capricorn", "козерог");
        zodiacMap.put("aquarius", "водолей");
        zodiacMap.put("pisces", "рыбы");
    }

    private static final Logger logger = LoggerFactory.getLogger(HoroscopeService.class);

    public HoroscopeService(UsersService usersService, MessageSender sender, HoroscopeClient horoscopeClient) {
        this.usersService = usersService;
        this.sender = sender;
        this.horoscopeClient = horoscopeClient;
    }

    public void sendHoroscope(CallbackQuery callback, String zodiacCode, String day) {
        Long chatID = callback.getMessage().getChatId();
        String userName = usersService.getUserNameByID(callback.getFrom().getId());

        String horoscopeText;
        try {
            horoscopeText = horoscopeFor(zodiacCode, day);
        } catch (Exception e) {
            logger.error("Не удалось получить гороскоп для {}: {}", zodiacCode, e.toString());
            sender.sendMessage(chatID, UNAVAILABLE_MESSAGE);
            return;
        }

        String signName = zodiacMap.getOrDefault(zodiacCode, zodiacCode).toUpperCase();
        String msg = "Гороскоп на " + dateFor(day).format(DATE_FORMAT)
                + " для знака зодиака \"" + signName + "\" по просьбе " + userName + "\n" + horoscopeText;

        sender.sendMessage(chatID, msg);
    }

    String horoscopeFor(String zodiacCode, String day) throws IOException {
        LocalDate today = LocalDate.now();
        if (!today.equals(cacheDate)) {
            cache.clear();
            cacheDate = today;
        }

        String key = zodiacCode + "|" + day;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String text = horoscopeClient.fetch(zodiacCode, day);
        cache.put(key, text);
        return text;
    }

    private static LocalDate dateFor(String day) {
        return "tomorrow".equals(day) ? LocalDate.now().plusDays(1) : LocalDate.now();
    }
}
