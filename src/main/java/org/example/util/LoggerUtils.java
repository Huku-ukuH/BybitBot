package org.example.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtils {
    private LoggerUtils() {
    }

        // Получить логгер для любого класса
        public static Logger getLogger(Class<?> clazz) {
            return LoggerFactory.getLogger(clazz);
        }

        public static void logInfo(String message) {
            getLogger(getCallerClass()).info("\n" + EmojiUtils.ZOOM + message + "\n");
        }

        public static void logDebug(String message) {
            getLogger(getCallerClass()).debug("\n" + EmojiUtils.DEBUG + message + "\n");
        }

        public static void logWarn(String message) {
            getLogger(getCallerClass()).warn("\n" + EmojiUtils.WARN + message + "\n");
        }

        public static void logError(String message, Throwable throwable) {
            getLogger(getCallerClass()).error("\n" + EmojiUtils.ERROR + message + "\n" + EmojiUtils.INFO, throwable + "\n");
        }

        private static Class<?> getCallerClass() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();

                // Пропускаем системные, логирующие и служебные пакеты
                if (className.startsWith("java.") ||
                        className.startsWith("javax.") ||
                        className.startsWith("sun.") ||
                        className.startsWith("org.slf4j.") ||
                        className.equals(LoggerUtils.class.getName())) {
                    continue;
                }

                // Теперь фильтруем по твоему базовому пакету
                if (className.startsWith("org.example")) {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        // просто игнорируем
                    }
                }
            }

            return LoggerUtils.class;
        }

}

