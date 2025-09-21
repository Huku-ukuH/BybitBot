package org.example.bot;

import lombok.Getter;
import lombok.Setter;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Getter
@Setter
public class MessageSender {
    private final TradingBot bot;
    private final ButtonManager buttonManager = new ButtonManager();

    public MessageSender(TradingBot bot) {
        this.bot = bot;
    }

    public void send(long chatId, String msg) {
        send(chatId, msg, null);
    }

    public void sendWithButtons(long chatId, String msg, List<String> buttonLabels) {
        ReplyKeyboard keyboard = (buttonLabels != null && !buttonLabels.isEmpty())
                ? buttonManager.createKeyboard(buttonLabels)
                : null;
        send(chatId, msg, keyboard);
    }

    public void sendAndClearButtons(long chatId, String msg) {
        send(chatId, msg, buttonManager.createClearKeyboard());
    }

    private void send(long chatId, String msg, ReplyKeyboard replyKeyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(msg);
        message.setReplyMarkup(replyKeyboard);

        try {
            bot.execute(message);
            LoggerUtils.info(EmojiUtils.ROBO + "(" + chatId + "): " + truncate(msg));
        } catch (TelegramApiException e) {
            LoggerUtils.error("Не удалось отправить сообщение в Telegram", e);
            // НЕ делай e.printStackTrace() — логгер уже всё записал
        }
    }

    // Полезно для длинных сообщений
    private String truncate(String str) {
        int maxLenght = 100;
        return str.length() <= maxLenght ? str : str.substring(0, maxLenght) + "...";
    }

    // Предупреждение пользователю + лог
    public void sendWarn(long chatId, String msg, String context) {
        send(chatId, EmojiUtils.WARN + " " + msg);
        LoggerUtils.warn("(" + chatId + ") " + msg + " [method=" + context + "]");
    }

    // Ошибка пользователю + лог с исключением
    public void sendError(long chatId, String userMsg, Exception ex, String context) {
        send(chatId, EmojiUtils.ERROR + " " + userMsg);
        LoggerUtils.error("(" + chatId + ") " + context + ": " + userMsg, ex);
    }
}