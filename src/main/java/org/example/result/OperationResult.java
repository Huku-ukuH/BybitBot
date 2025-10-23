package org.example.result;

import lombok.Getter;
import org.example.util.LoggerUtils;

@Getter
public final class OperationResult {
    private final boolean success;
    private final String message;
    private final Throwable cause; // ← опционально, для ошибок

    private OperationResult(boolean success, String message, Throwable cause) {
        this.success = success;
        this.message = message;
        this.cause = cause;
    }

    public static OperationResult success() {
        return new OperationResult(true, "OK",null);
    }

    public static OperationResult success(String message) {
        return new OperationResult(true, message, null);
    }

    public static OperationResult failure(String message) {
        return new OperationResult(false, message, null);
    }

    public static OperationResult failure(String message, Throwable cause) {
        // Сохраняем причину и её стек
        return new OperationResult(false, message + " | " + cause.toString(), cause);
    }
    public void logErrorIfFailed() {
        if (!success && cause != null) {
            LoggerUtils.error(message, cause); // ← вот где стек попадёт в лог!
        }
    }
}





