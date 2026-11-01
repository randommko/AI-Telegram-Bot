package org.example.CockSize;


import com.vdurmont.emoji.EmojiParser;
import org.example.DTO.AVGCockSizeDTO;
import org.example.MessageSender;
import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class CockSizeGame {
    private static final Logger logger = LoggerFactory.getLogger(CockSizeGame.class);
    private final CockSizeService service;
    private final UsersService usersService;
    private final MessageSender sender;
    private static final String RESOURCES_PATH = "/bin/tg_bot/resources";

    public CockSizeGame(CockSizeService service, UsersService usersService, MessageSender sender) {
        this.service = service;
        this.usersService = usersService;
        this.sender = sender;
    }
    public void sendTodayCockSize(Message message) {
        Integer userSize = null;
        String messageForFoundSize = null;
        Long chatID = message.getChatId();

        Map<Integer, String> sizeMap = getCockSize(message.getFrom().getId());
        for (Map.Entry<Integer, String> entry : sizeMap.entrySet()) {
            userSize = entry.getKey();
            messageForFoundSize = entry.getValue();
        }

        String imgFileName = getCockSizeImageName(userSize);
        String filePath = RESOURCES_PATH + "/" + imgFileName;

        InputFile inputFile = new InputFile(new File(filePath));

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatID);
        sendPhoto.setPhoto(inputFile); // Ссылка на изображение или файл
        sendPhoto.setCaption(messageForFoundSize);

        // Добавление inline-кнопок
        InlineKeyboardMarkup markup = createInlineKeyboard();
        sendPhoto.setReplyMarkup(markup);
        try {
            sender.getBot().execute(sendPhoto);
        } catch (Exception e) {
            logger.error("Ошибка отправки длинны члена с картинкой {}", String.valueOf(e));
            sender.sendMessage(chatID, messageForFoundSize);
        }
    }
    private Map<Integer, String> getCockSize(Long userID) {
        //Ключ - длинна, Значение - сообщение соответсвующее длине
        Map<Integer, String> result = new HashMap<>();

        Integer playerTodayCockSize = service.findTodayCockSize(userID);
        String msgText = service.phraseSelection(playerTodayCockSize, service.getUserNameByID(userID));

        if (playerTodayCockSize != -1) {
            result.put(playerTodayCockSize, msgText);
            return result;
        }

        Integer playerNewCockSize = service.measureCockSize(userID);
        msgText = service.phraseSelection(playerNewCockSize, service.getUserNameByID(userID));

        result.put(playerNewCockSize, msgText);

        return result;
    }
    private String getCockSizeImageName(Integer size) {
        return service.getCockSizeImageName(size);
    }
    public void sendAVGCockSize(CallbackQuery callbackQuery) {
        Long userID= callbackQuery.getFrom().getId();
        Long chatID = callbackQuery.getMessage().getChatId();
        String userName = usersService.getUserNameByID(userID);
        AVGCockSizeDTO result = service.getAVGCockSize(userID);

        if (result == null) {
            sender.sendMessage(chatID, userName + " ни разу не измерял свой член!");
            return;
        }
        float avgSize = result.AVGSize();
        String avgSizeFormatted = String.format("%.2f", avgSize); // Ограничиваем до 2 знаков после запятой

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        String formattedFirstMeasurementDate = result.firstMeasurementDate().format(formatter);
        String formattedLastMeasurementDate = result.lastMeasurementDate().format(formatter);

        sender.sendMessage(chatID, EmojiParser.parseToUnicode("C " + formattedFirstMeasurementDate + " по " +
                formattedLastMeasurementDate + " " + userName + " сделал " +
                result.measurementCount() + " замеров.\nВ среднем твой болт\uD83C\uDF46: " + avgSizeFormatted + "cm"));
    }
    private InlineKeyboardMarkup createInlineKeyboard() {
        InlineKeyboardButton AVGCockSize = new InlineKeyboardButton();
        AVGCockSize.setText(EmojiParser.parseToUnicode("\uD83D\uDCA6\uD83C\uDF46\uD83D\uDCA6  Статистика  \uD83D\uDCA6\uD83C\uDF46\uD83D\uDCA6"));
        AVGCockSize.setCallbackData("avg_cock_size_button_pressed");

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(AVGCockSize);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row1);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        return markup;
    }
}
