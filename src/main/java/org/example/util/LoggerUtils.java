package org.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtils {

    private static final String BASE_PACKAGE = "org.example";

    private LoggerUtils() {}

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    // Автоматически определяет класс, вызвавший логгер
    private static Logger getCurrentLogger() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length; i++) { // начинаем с 2, чтобы пропустить getStackTrace и getCurrentLogger
            String className = stack[i].getClassName();
            if (!className.equals(LoggerUtils.class.getName()) &&
                    className.startsWith(BASE_PACKAGE)) {
                try {
                    return LoggerFactory.getLogger(Class.forName(className));
                } catch (ClassNotFoundException ignored) {}
            }
        }
        return LoggerFactory.getLogger(LoggerUtils.class);
    }

    public static void info(String message) {
        getCurrentLogger().info("{} {}", EmojiUtils.ZOOM, message);
    }

    public static void debug(String message) {
        getCurrentLogger().debug("{} {}", EmojiUtils.DEBUG, message);
    }

    public static void warn(String message) {
        getCurrentLogger().warn("{} {}", EmojiUtils.WARN, message);
    }

    public static void error(String message, Throwable throwable) {
        getCurrentLogger().error("{} {}{}", EmojiUtils.ERROR, message,
                "", throwable);
    }

    public static void error(String message) {
        getCurrentLogger().error("{} {}", EmojiUtils.ERROR, message);
    }
}