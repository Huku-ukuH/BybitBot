package org.example.bot;

import lombok.Getter;
import lombok.Setter;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;

@Getter
@Setter
public class MessageSender {

    TradingBot bot;

    public MessageSender(TradingBot bot){
        this.bot = bot;
    }

    public void send(long chatId, String msg) {
            bot.sendMessage(chatId, msg);
            LoggerUtils.logInfo(EmojiUtils.ROBO + "(" + chatId + "): " + msg);
    }
    public void sendWarn(long chatId, String msg, String method) {
        bot.sendMessage(chatId, EmojiUtils.WARN + " " + msg);
        LoggerUtils.logWarn(EmojiUtils.ROBO + " (" + chatId + "): " + msg + ": " + method);
    }
    public void sendError(long chatId, String msg, Exception ex, String method) {
            bot.sendMessage(chatId, EmojiUtils.ERROR +  " " + msg);
            LoggerUtils.logError(EmojiUtils.ROBO + " (" + chatId + "): " + method + ": " + msg + " CAUSE: ", ex);
    }
}
