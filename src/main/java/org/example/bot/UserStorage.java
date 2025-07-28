package org.example.bot;

import org.example.util.LoggerUtils;

import java.util.HashMap;
import java.util.Map;

public class UserStorage {

    private final Map<Long, TelegramUser> users = new HashMap<>();

    public synchronized void addAdminUser(long chatId) {
            users.put(chatId, new TelegramUser(chatId, TelegramUser.AccessLevel.PREMIUM));
    }
    public synchronized void addUserIfNotExists(long chatId) {
            users.put(chatId, new TelegramUser(chatId, TelegramUser.AccessLevel.BASIC));
    }
    public synchronized void addBlockedUser(long chatId) {
        if (!users.containsKey(chatId)) {
            users.put(chatId, new TelegramUser(chatId, TelegramUser.AccessLevel.BLOCKED));
        }
    }

    public synchronized TelegramUser getUser(long chatId) {
        return users.get(chatId);
    }

    public synchronized void setAccessLevel(long chatId, TelegramUser.AccessLevel level) {
        if (users.containsKey(chatId)) {
            users.get(chatId).setAccessLevel(level);
            return;
        }
        LoggerUtils.logWarn("Пользователь с таким chatId не найден");
    }

    public synchronized boolean containsUser(long chatId) {
        return users.containsKey(chatId);
    }

    public synchronized Map<Long, TelegramUser> getAllUsers() {
        return new HashMap<>(users);
    }

    public boolean isBlocked(long chatId) {
        return getUser(chatId).getAccessLevel() == TelegramUser.AccessLevel.BLOCKED;
    }
    public boolean isPremium(long chatId) {
        return getUser(chatId).getAccessLevel() == TelegramUser.AccessLevel.PREMIUM;
    }
}