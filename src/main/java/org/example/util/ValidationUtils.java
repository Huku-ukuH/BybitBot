package org.example.util;


// методы валидации значений (например, проверка на положительность числа)
public class ValidationUtils {
    public static void checkNotNull(Object obj, String message) {
        if (obj == null) {
            IllegalArgumentException exception = new IllegalArgumentException(message);
            LoggerUtils.logError(message, exception);
            throw exception;
        }
    }

    public static void checkPositive(double value, String message) {
        if (value <= 0) {
            IllegalArgumentException exception = new IllegalArgumentException(message);
            LoggerUtils.logError(message, exception);
            throw exception;        }
    }

    public static void checkNonNegative(double value, String message) {
        if (value < 0) {
            IllegalArgumentException exception = new IllegalArgumentException(message);
            LoggerUtils.logError(message, exception);
            throw exception;        }
    }

    public static void checkTrue(boolean condition, String message) {
        if (!condition) {
            IllegalArgumentException exception = new IllegalArgumentException(message);
            LoggerUtils.logError(message, exception);
            throw exception;        }
    }

    private ValidationUtils() {
    }
}