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
    private final ButtonManager buttonManager = new ButtonManager(); // Внедряем ButtonManager

    public MessageSender(TradingBot bot) {
        this.bot = bot;
    }

    /**
     * Отправляет простое сообщение без кнопок.
     */
    public void send(long chatId, String msg) {
        send(chatId, msg, null); // Передаем null, чтобы не добавлять клавиатуру
    }

    /**
     * Отправляет сообщение с клавиатурой.
     *
     * @param chatId ID чата.
     * @param msg Текст сообщения.
     * @param buttonLabels Список названий кнопок. Если null или пустой — клавиатура не добавляется.
     */
    public void sendWithButtons(long chatId, String msg, List<String> buttonLabels) {
        send(chatId, msg, buttonLabels != null && !buttonLabels.isEmpty() ? buttonManager.createKeyboard(buttonLabels) : null);
    }

    /**
     * Отправляет сообщение и полностью удаляет текущую клавиатуру у пользователя.
     */
    public void sendAndClearButtons(long chatId, String msg) {
        send(chatId, msg, buttonManager.createClearKeyboard());
    }

    /**
     * Основной приватный метод, который инкапсулирует логику отправки.
     *
     * @param chatId ID чата.
     * @param msg Текст сообщения.
     * @param replyKeyboard Клавиатура (может быть ReplyKeyboardMarkup или ReplyKeyboardRemove, или null).
     */
    private void send(long chatId, String msg, ReplyKeyboard replyKeyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(msg);

        if (replyKeyboard != null) {
            message.setReplyMarkup(replyKeyboard);
        }

        try {
            bot.execute(message); // Используем execute напрямую, если TradingBot наследует TelegramLongPollingBot
            LoggerUtils.logInfo(EmojiUtils.ROBO + "(" + chatId + "): " + msg);
        } catch (TelegramApiException e) {
            LoggerUtils.logError("Ошибка отправки сообщения telegram", e);
            e.printStackTrace();
        }
    }

    // Существующие методы для предупреждений и ошибок (можно оставить без кнопок)
    public void sendWarn(long chatId, String msg, String method) {
        send(chatId, EmojiUtils.WARN + " " + msg, null);
        LoggerUtils.logWarn(EmojiUtils.ROBO + " (" + chatId + "): " + msg + ": " + method);
    }

    public void sendError(long chatId, String msg, Exception ex, String method) {
        send(chatId, EmojiUtils.ERROR + " " + msg, null);
        LoggerUtils.logError(EmojiUtils.ROBO + " (" + chatId + "): " + method + ": " + msg + " CAUSE: ", ex);
    }
}