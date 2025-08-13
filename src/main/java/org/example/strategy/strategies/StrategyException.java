package org.example.strategy.strategies;

/**
 * Исключение, выбрасываемое стратегиями при ошибках выполнения.
 */
public class StrategyException extends Exception {

    /**
     * Конструктор с сообщением об ошибке.
     *
     * @param message Сообщение об ошибке.
     */
    public StrategyException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением об ошибке и причиной.
     *
     * @param message Сообщение об ошибке.
     * @param cause   Причина исключения.
     */
    public StrategyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Конструктор с причиной.
     *
     * @param cause Причина исключения.
     */
    public StrategyException(Throwable cause) {
        super(cause);
    }
}