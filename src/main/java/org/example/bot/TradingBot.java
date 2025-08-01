package org.example.bot;

import io.github.cdimascio.dotenv.Dotenv;
import org.example.util.LoggerUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TradingBot extends TelegramLongPollingBot {
    private final BotCommandHandler commandHandler;
    private final UserStorage userStorage = new UserStorage();


    public TradingBot(BotCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
        userStorage.addAdminUser(340827223L);
        userStorage.addUserIfNotExists(949310446); // сня
        userStorage.addUserIfNotExists(1804232343); //his
        userStorage.addBlockedUser(-1002610493065L); // чат
        userStorage.addUserIfNotExists(987654321L);
    }

    @Override
    public String getBotUsername() {
        return Dotenv.load().get("BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        return Dotenv.load().get("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getChat().getUserName();
            String firstName = update.getMessage().getChat().getFirstName();

            LoggerUtils.logInfo("\n\uD83D\uDCAC @" + username + "(" + chatId + ") " + firstName + ": " + messageText);

            userStorage.addBlockedUser(chatId);
            if (userStorage.isBlocked(chatId)) {
                sendMessage(chatId, "Error: 473404 more info - @clamser");
                return;
            }

            // Команда /start
            if (messageText.equals("/start")) {
                sendMessage(chatId, "Добро пожаловать! Здесь вы можете пообщаться с GigaChat бесплатно :) ");
                return;
            }

            if (userStorage.isPremium(chatId)) {
                if (commandHandler.isJustChat()) {
                    sendMessage(chatId, commandHandler.getAiService().justChat(messageText));
                    return;
                }
                if (commandHandler.isWaitingSignal()) {
                    messageText = "/getsgnl " + messageText;
                }

                if (messageText.startsWith("/")) {
                    String[] parts = messageText.split(" ", 2);
                    String command = parts[0];
                    String args = parts.length > 1 ? parts[1] : "";
                    commandHandler.handleCommand(chatId, command, args);
                }
                return;
            }

            sendMessage(chatId, commandHandler.getAiService().justChat(messageText));
        }
    }


    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            LoggerUtils.logError("Ошибка отправки сообщения telegram", e);
            e.printStackTrace();
        }
    }
}