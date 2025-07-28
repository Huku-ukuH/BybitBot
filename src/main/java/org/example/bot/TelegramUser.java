package org.example.bot;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelegramUser {


    @Getter
    public enum AccessLevel {
        BLOCKED,
        BASIC,
        PREMIUM
    }

    private final long chatId;
    private AccessLevel accessLevel;

    public TelegramUser(long chatId, AccessLevel accessLevel) {
        this.chatId = chatId;
        this.accessLevel = accessLevel;
    }
}