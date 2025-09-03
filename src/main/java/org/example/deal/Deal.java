package org.example.deal;

import lombok.Getter;
import lombok.Setter;
import org.example.model.Symbol;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.deal.dto.DealRequest;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.strategies.StrategyFactory;
import org.example.strategy.strategies.TradingStrategy;
import org.example.util.LoggerUtils;
import org.example.util.MathUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class Deal {
    // === Основные поля сделки ===
    private String id;  // Уникальный ID сделки
    private String note;
    private long chatId;
    private Symbol symbol;
    private Double stopLoss;
    private ExitPlan exitPlan;
    private Double entryPrice;
    private Direction direction;
    private double positionSize;
    private double leverageUsed;
    private EntryType entryType;
    private Double potentialLoss;
    private double requiredCapital;
    private List<Double> takeProfits;
    private PositionInfo positionInfo;
    private List <OrderManager> ordersIdList;
    private List<String> executedTpOrderIds = new ArrayList<>(); // Для трейлинга (пока просто переменная)


    private String strategyName = "ai";
    private transient TradingStrategy strategy;

    private boolean active = true;
    private boolean positivePnL = false;
    private List<ExitStep> executedExits = new ArrayList<>();
    private Map<Double, Integer> tpToPercentage = new HashMap<>();

    public Deal(Symbol symbol, Direction direction, EntryType entryType, Double entryPrice,
                Double stopLoss, List<Double> takeProfits) {
        this.id = "default";
        this.symbol = symbol;
        this.direction = direction;
        this.entryType = entryType;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfits = new ArrayList<>(takeProfits != null ? takeProfits : Collections.emptyList());
        this.positionSize = 0.0;
        this.leverageUsed = 1;
        this.requiredCapital = 0.0;
        this.ordersIdList = new ArrayList<>();
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


    public void updateFromPosition(PositionInfo positionInfo){
        if (positionInfo == null || !this.symbol.getSymbol().equals(positionInfo.getSymbol())) {
            return;
        }
        this.positionInfo = positionInfo;
        leverageUsed = positionInfo.getLeverage();
        positionSize = positionInfo.getSize();
        potentialLoss = positionInfo.getPotentialLoss();
    }
    // === Логика управления сделкой ===



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
    public boolean isPositivePNL() {
        if (positionInfo != null) {
            positivePnL = positionInfo.getUnrealizedPnl() > 0;
            return positivePnL;
        }
        return false;
    }


    @Override
    public String toString() {
        return "🟢\\\"" + strategyName + "\uD83E\uDDE0\\\" " + symbol + "—" + direction.toString().toLowerCase() + entryType + "\n" +
                "💸 Цена входа: ~" + entryPrice +
                "🛑 SL: " + stopLoss + "\n" +
                "✅ TP: " + takeProfits + "\n";
    }

    public String bigDealToString() {
        return id + "\n" + this + "QTY: " + positionSize + "\n" + "Риск: " + potentialLoss + "$\n";
    }

    public StringBuilder positiveDeal() {
        StringBuilder sb = new StringBuilder(symbol + "\n");
        sb.append(isPositivePNL()? "🛑" : "✅ БУ");
        sb.append(positionInfo.toString());
        return sb;
    }

    public void addOrderId(OrderManager order) {
        this.ordersIdList.add(order);
    }

    public List<OrderManager> getOrdersIdList() {
        return Collections.unmodifiableList(ordersIdList);
    }

    public void clearOrdersIdList() {
        this.ordersIdList.clear();
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
    }

    // equals и hashCode можно добавить при необходимости, например, для хранения в Set
}