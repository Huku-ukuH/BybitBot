
package org.example.bybit.client;

        import org.example.util.LoggerUtils;

        import java.util.concurrent.atomic.AtomicInteger;
        import java.util.concurrent.locks.Lock;
        import java.util.concurrent.locks.ReentrantLock;

/**
 * Простой ограничитель частоты запросов.
 * При превышении лимита запросов за период приостанавливает дальнейшие попытки на заданное время.
 * Не гарантирует точного соблюдения лимита на уровне клиента, но предотвращает его быстрое исчерпание.
 */
public class RateLimiter { //класс подсчета запросов для Http Client

    private final int maxRequests;
    private final long periodMillis;
    private final long pauseMillis;

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile long periodStart;
    private volatile boolean isPaused = false;
    private final Lock pauseLock = new ReentrantLock(); // Для синхронизации проверки паузы

    /**
     * Конструктор.
     *
     * @param maxRequests Максимальное количество запросов за период.
     * @param periodMillis Длительность периода в миллисекундах (например, 60000 для 1 минуты).
     * @param pauseMillis Длительность паузы в миллисекундах при превышении лимита (например, 10000 для 10 секунд).
     */
    public RateLimiter(int maxRequests, long periodMillis, long pauseMillis) {
        this.maxRequests = maxRequests;
        this.periodMillis = periodMillis;
        this.pauseMillis = pauseMillis;
        this.periodStart = System.currentTimeMillis();
    }

    /**
     * Проверяет, можно ли выполнить запрос. Если лимит превышен, поток засыпает на pauseMillis.
     * Метод должен вызываться перед отправкой каждого запроса.
     *
     * @throws InterruptedException если поток был прерван во время ожидания.
     */
    public void acquire() throws InterruptedException {
        long now = System.currentTimeMillis();

        // Проверка, нужно ли сбросить счетчик (начался новый период)
        if (now - periodStart > periodMillis) {
            synchronized (this) {
                // Повторная проверка внутри synchronized блока на случай гонки
                if (now - periodStart > periodMillis) {
                    requestCount.set(0);
                    periodStart = now;
                    isPaused = false; // Сброс паузы при начале нового периода
                    LoggerUtils.debug("RateLimiter: Новый период. Счетчик сброшен.");
                }
            }
        }

        // Проверка и ожидание, если активна пауза
        pauseLock.lock();
        try {
            if (isPaused) {
                LoggerUtils.warn("RateLimiter: Достигнут лимит. Ожидание " + pauseMillis + " мс перед следующим запросом...");
                Thread.sleep(pauseMillis);
                // После паузы сбрасываем счетчик и период
                synchronized (this) {
                    requestCount.set(0);
                    periodStart = System.currentTimeMillis();
                    isPaused = false;
                    LoggerUtils.info("RateLimiter: Пауза завершена. Счетчик сброшен.");
                }
            }
        } finally {
            pauseLock.unlock();
        }

        // Инкремент счетчика
        int currentCount = requestCount.incrementAndGet();

        // Проверка, не превышен ли лимит
        if (currentCount > maxRequests) {
            pauseLock.lock();
            try {
                // Повторная проверка внутри блокировки, чтобы избежать множественных пауз
                if (!isPaused) { // Если пауза еще не установлена другим потоком
                    isPaused = true;
                    LoggerUtils.info("RateLimiter: Превышен лимит запросов (" + maxRequests +
                            " за " + periodMillis + " мс). Установлена пауза на " + pauseMillis + " мс.");
                    // Пауза будет применена при следующем вызове acquire() или по истечении времени внутри acquire()
                    // Лучше сразу заснуть
                    LoggerUtils.warn("RateLimiter: Начало паузы на " + pauseMillis + " мс...");
                    Thread.sleep(pauseMillis);
                    synchronized (this) {
                        requestCount.set(0);
                        periodStart = System.currentTimeMillis();
                        isPaused = false;
                        LoggerUtils.info("RateLimiter: Пауза завершена. Счетчик сброшен.");
                    }
                } else {
                    // Если пауза уже установлена другим потоком, текущий поток тоже должен подождать
                    // и сбросить счетчик после окончания паузы
                    LoggerUtils.debug("RateLimiter: Поток ожидает окончания паузы, установленной другим потоком.");
                    Thread.sleep(pauseMillis); // Простое ожидание, счетчик будет сброшен первым потоком
                    // После ожидания, сбрасываем счетчик, если он еще не сброшен
                    synchronized (this) {
                        if (isPaused) { // Проверка на всякий случай
                            requestCount.set(0);
                            periodStart = System.currentTimeMillis();
                            isPaused = false;
                            LoggerUtils.info("RateLimiter: Счетчик сброшен после ожидания паузы.");
                        }
                    }
                }
            } finally {
                pauseLock.unlock();
            }
        }
        // Если лимит не превышен, запрос разрешен
        LoggerUtils.debug("RateLimiter: Запрос разрешен. Текущий счетчик: " + currentCount);
    }
}