package org.example.deal;

import lombok.Getter;
import lombok.Setter;
import org.example.deal.dto.PartialExitPlan;
import org.example.model.Symbol;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.deal.dto.DealRequest;
import org.example.strategy.StrategyFactory;
import org.example.strategy.TradingStrategy;
import org.example.util.LoggerUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class Deal {
    // === Основные поля сделки ===
    private final String id;  // Уникальный ID сделки
    private Symbol symbol;
    private Direction direction;
    private EntryType entryType;
    private Double entryPrice;
    private Double stopLoss;
    private Double potentialLoss;
    private List<Double> takeProfits;
    private double positionSize;
    private int leverageUsed;
    private double requiredCapital;
    private String note;
    private long chatId;

    private String strategyName = "ai";
    private transient TradingStrategy strategy;

    private boolean minQty = false;
    private boolean active = true;
    private boolean positivePnL = false;
    private List<ExitStep> executedExits = new ArrayList<>();
    private Map<Double, Integer> tpToPercentage = new HashMap<>();

    public Deal(Symbol symbol, Direction direction, EntryType entryType, Double entryPrice,
                Double stopLoss, List<Double> takeProfits) {
        this.id = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.direction = direction;
        this.entryType = entryType;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfits = new ArrayList<>(takeProfits != null ? takeProfits : Collections.emptyList());
        this.positionSize = 0.0;
        this.leverageUsed = 1;
        this.requiredCapital = 0.0;
    }

    public Deal(DealRequest request) {
        this(
                request.getSymbol(),
                request.getDirection(),
                request.getEntryType(),
                request.getEntryPrice(),
                request.getStopLoss(),
                request.getTakeProfits()
        );
    }

    public void setStrategyName(String strategyName) {
        if (strategyName == null || strategyName.isEmpty()) {
            LoggerUtils.logWarn("Попытка установить пустое или null имя стратегии для сделки " + this.id + ". Игнорируется.");
            return;
        }
        this.strategyName = strategyName.toLowerCase();
        this.strategy = null;
        LoggerUtils.logDebug("Стратегия для сделки " + this.id + " установлена на: " + this.strategyName);
    }
    //Получает экземпляр стратегии, связанной с этой сделкой.
    public TradingStrategy getStrategy() {
        if (strategy == null && strategyName != null && !strategyName.isEmpty()) {
            try {
                this.strategy = StrategyFactory.getStrategy(this.strategyName);
                LoggerUtils.logDebug("Экземпляр стратегии '" + this.strategyName + "' успешно создан для сделки " + this.id);

            } catch (Exception e) { // Перехватываем общее исключение на случай проблем в фабрике
                LoggerUtils.logError("Не удалось загрузить стратегию '" + strategyName + "' для сделки " + this.id + ". Попытка отката к 'ai'.", e);

                // Попытка отката к стратегии по умолчанию
                if (!"ai".equals(this.strategyName)) { // Избегаем бесконечной рекурсии
                    try {
                        this.strategyName = "ai";
                        this.strategy = StrategyFactory.getStrategy(this.strategyName);
                        LoggerUtils.logWarn("Стратегия для сделки " + this.id + " откачена к 'ai'.");
                    } catch (Exception fallbackException) {
                        LoggerUtils.logError("Критическая ошибка: Не удалось загрузить даже стратегию 'ai' для сделки " + this.id, fallbackException);
                        throw new RuntimeException("Не удалось инициализировать стратегию для сделки " + this.id, fallbackException);
                    }
                } else {
                    LoggerUtils.logError("Критическая ошибка: Не удалось загрузить стратегию 'ai' для сделки " + this.id, e);
                    throw new RuntimeException("Не удалось инициализировать стратегию 'ai' для сделки " + this.id, e);
                }
            }
        }
        return strategy;
    }

    public List<Double> getTakeProfits() {
        return Collections.unmodifiableList(takeProfits);
    }

    public void addTakeProfit(double tp) {
        if (!takeProfits.contains(tp)) {
            takeProfits.add(tp);
        }
    }

    // === Логика управления сделкой ===

    /**
     * Применяет план частичного выхода к сделке.
     * Добавляет TP из плана в список takeProfits и заполняет карту tpToPercentage.
     *
     * @param plan План частичного выхода.
     */
    public void applyPartialExitPlan(PartialExitPlan plan) {
        if (plan == null || plan.getPartialExits() == null) {
            LoggerUtils.logWarn("Попытка применить null или пустой план PartialExitPlan к сделке " + this.id);
            return;
        }
        for (PartialExitPlan.ExitStep step : plan.getPartialExits()) {
            tpToPercentage.put(step.getTakeProfit(), step.getPercentage());
            addTakeProfit(step.getTakeProfit()); // addTakeProfit проверит дубликаты
        }
        LoggerUtils.logDebug("План частичного выхода применен к сделке " + this.id);
    }

    /**
     * Зафиксирован выход по одному из TP.
     * Обновляет список выполненных выходов и, при необходимости, помечает сделку как неактивную.
     *
     * @param exitPrice Цена выхода.
     * @param exitAmount Количество вышедших контрактов/монет.
     */
    public void recordExit(double exitPrice, double exitAmount) {
        if (!active) {
            LoggerUtils.logWarn("Попытка записать выход для неактивной сделки " + this.id);
            return;
        }
        if (!takeProfits.contains(exitPrice)) {
            LoggerUtils.logWarn("Попытка записать выход по неизвестному TP (" + exitPrice + ") для сделки " + this.id);
            return;
        }

        ExitStep exit = new ExitStep(exitPrice, exitAmount);
        executedExits.add(exit);
        LoggerUtils.logDebug("Зарегистрирован выход: цена=" + exitPrice + ", количество=" + exitAmount + " для сделки " + this.id);

        // Если все TP выполнены — сделка считается закрытой
        // Используем >= на случай, если выходов больше, чем TP (например, Market order закрыл всё)
        if (executedExits.size() >= takeProfits.size() && !takeProfits.isEmpty()) {
            this.active = false;
            LoggerUtils.logInfo("Сделка " + this.id + " помечена как неактивная, так как все TP выполнены.");
        }
    }

    /**
     * Рассчитывает прибыль/убыток (PnL) на основе текущей цены.
     *
     * @param currentPrice Текущая рыночная цена.
     * @return PnL в абсолютных единицах (например, USDT).
     */
    public double calculatePnL(double currentPrice) {
        if (positionSize == 0 || entryPrice == null) {
            LoggerUtils.logDebug("PnL не может быть рассчитан: positionSize=" + positionSize + ", entryPrice=" + entryPrice + " для сделки " + this.id);
            return 0.0;
        }

        double profitPerUnit;
        if (direction == Direction.LONG) {
            profitPerUnit = currentPrice - entryPrice;
        } else {
            profitPerUnit = entryPrice - currentPrice;
        }

        double pnl = profitPerUnit * positionSize;
        LoggerUtils.logInfo("Рассчитан PnL для сделки " + this.id + ": " + pnl + " (цена=" + currentPrice + ")");
        return pnl;
    }

    /**
     * Рассчитывает возврат на инвестиции (ROI) в процентах.
     *
     * @param currentPrice Текущая рыночная цена.
     * @return ROI в процентах.
     */
    public double calculateROI(double currentPrice) {
        if (requiredCapital <= 0) {
            LoggerUtils.logDebug("ROI не может быть рассчитан: requiredCapital=" + requiredCapital + " для сделки " + this.id);
            return 0.0;
        }

        double pnl = calculatePnL(currentPrice);
        double roi = (pnl / requiredCapital) * 100;
        LoggerUtils.logInfo("Рассчитан ROI для сделки " + this.id + ": " + String.format("%.2f", roi) + "%");
        return roi;
    }

    /**
     * Возвращает количество оставшихся (еще не выполненных) TP.
     *
     * @return Количество оставшихся TP.
     */
    public int getRemainingTakeProfitsCount() {
        AtomicInteger count = new AtomicInteger(0); // Инициализируем счетчик
        takeProfits.forEach(tp -> {
            // Проверяем, был ли уже выход по этому TP
            boolean executed = executedExits.stream().anyMatch(e -> Double.compare(e.getExitPrice(), tp) == 0);
            if (!executed) {
                count.incrementAndGet();
            }
        });
        int remaining = count.get();
        LoggerUtils.logInfo("Оставшиеся TP для сделки " + this.id + ": " + remaining);
        return remaining;
    }

    @Override
    public String toString() {
        return "🟢 Сделка " + symbol + " — " + direction.toString().toLowerCase() + "\n\n" +
                entryType + "\n" +
                (entryType == EntryType.LIMIT ?
                        "💸 Цена входа: ~" + entryPrice :
                        "💰 Текущая цена: " + entryPrice) + "\n" +
                "🛑 SL: " + stopLoss + "\n" +
                "✅ TP: " + takeProfits + "\n" +
                "🧠 Стратегия: " + strategyName + "\n"; // Добавляем информацию о стратегии
    }

    public String theBigToString() {
        StringBuilder sb = new StringBuilder();

        sb.append("🟢").append(symbol)
                .append(" — ").append(direction.toString().toLowerCase())
                .append("\n\n");

        sb.append("📌 Вход: ").append(entryType.toString().toLowerCase()).append("\n");
        sb.append(entryType == EntryType.LIMIT
                        ? "💸 Price: " + entryPrice
                        : "💰 Current: " + entryPrice)
                .append("\n");

        sb.append("🛑 SL: ").append(stopLoss).append("\n");
        sb.append("✅ TP: ").append(takeProfits).append("\n");

        sb.append("📐QTY ").append(String.format("%.4f", positionSize)).append("\n"); // Форматирование чисел
        sb.append("🔁LEV: ").append(leverageUsed).append("\n");
        sb.append("💰CAP: ").append(String.format("%.2f", requiredCapital)).append("\n"); // Форматирование чисел
        sb.append("🧠 Стратегия: ").append(strategyName).append("\n"); // Добавляем информацию о стратегии

        sb.append("📝 Примечание: ").append(note != null ? note : "-").append("\n");
        return sb.toString();
    }

    // === Вспомогательные классы ===

    @Getter
    public static class ExitStep {
        private final double exitPrice;
        private final double exitAmount;

        public ExitStep(double exitPrice, double exitAmount) {
            this.exitPrice = exitPrice;
            this.exitAmount = exitAmount;
        }

        @Override
        public String toString() {
            return "ExitStep{" +
                    "exitPrice=" + exitPrice +
                    ", exitAmount=" + exitAmount +
                    '}';
        }
    }

    // equals и hashCode можно добавить при необходимости, например, для хранения в Set
}