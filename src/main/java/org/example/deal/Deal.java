package org.example.deal;

import lombok.Getter;
import lombok.Setter;
import org.example.deal.utils.OrderManager;
import org.example.model.Symbol;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.deal.dto.DealRequest;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.strategies.strategies.superStrategy.AbstractStrategy;
import org.example.strategy.strategies.strategies.StrategyFactory;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
@Setter
public class Deal {
    // === Основные поля сделки ===
    private String id;  // Уникальный ID сделки
    private long chatId;
    private String note;
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
    private AbstractStrategy strategy;

    private boolean active = false;
    private boolean positivePnL = false;
    private List<ExitStep> executedExits = new ArrayList<>();
    private Map<Double, Integer> tpToPercentage = new HashMap<>();

    public Deal(Symbol symbol, Direction direction, EntryType entryType, Double entryPrice,
                Double stopLoss, List<Double> takeProfits) {

        this.symbol = symbol;
        this.id = "default " + symbol + "DEFAULT_ID";
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
            LoggerUtils.warn("Попытка установить пустое или null имя стратегии для сделки " + this.id + ". Игнорируется.");
            return;
        }
        this.strategyName = strategyName.toLowerCase();
        this.strategy = null;
        LoggerUtils.debug("Стратегия для сделки " + this.id + " установлена на: " + this.strategyName);
    }
    //Получает экземпляр стратегии, связанной с этой сделкой.
    public AbstractStrategy getStrategy() {
        if (strategy == null && strategyName != null && !strategyName.isEmpty()) {
            try {
                this.strategy = StrategyFactory.getStrategy(this.strategyName);

            } catch (Exception e) { // Перехватываем общее исключение на случай проблем в фабрике
                LoggerUtils.error("Не удалось загрузить стратегию '" + strategyName + "' для сделки " + this.id + ". Попытка отката к 'ai'.", e);

                // Попытка отката к стратегии по умолчанию
                if (!"ai".equals(this.strategyName)) { // Избегаем бесконечной рекурсии
                    try {
                        this.strategyName = "ai";
                        this.strategy = StrategyFactory.getStrategy(this.strategyName);
                        LoggerUtils.warn("Стратегия для сделки " + this.id + " откачена к 'ai'.");
                    } catch (Exception fallbackException) {
                        LoggerUtils.error("Критическая ошибка: Не удалось загрузить даже стратегию 'ai' для сделки " + this.id, fallbackException);
                        throw new RuntimeException("Не удалось инициализировать стратегию для сделки " + this.id, fallbackException);
                    }
                } else {
                    LoggerUtils.error("Критическая ошибка: Не удалось загрузить стратегию 'ai' для сделки " + this.id, e);
                    throw new RuntimeException("Не удалось инициализировать стратегию 'ai' для сделки " + this.id, e);
                }
            }
        }
        return strategy;
    }

    public List<Double> getTakeProfits() {
        return Collections.unmodifiableList(takeProfits);
    }


    public void updateDealFromBybitPosition(PositionInfo positionInfo) {
        if (positionInfo == null) {
            LoggerUtils.info("Attempt to update deal with null PositionInfo");
            return;
        }

        // Сохраняем старые значения для лога
        double oldLeverage = this.leverageUsed;
        double oldPositionSize = this.positionSize;
        double oldPotentialLoss = potentialLoss == null? 0.0 : this.potentialLoss;
        double oldEntryPrice = this.entryPrice;
        double oldStopLoss = this.stopLoss;

        this.positionInfo = positionInfo;
        this.leverageUsed = positionInfo.getLeverage();
        this.positionSize = positionInfo.getSize();
        this.entryPrice = positionInfo.getAvgPrice();
        this.potentialLoss = Math.round(positionSize * Math.abs(entryPrice - stopLoss) * 1000.0) / 1000.0;
        double roi = getRoi();




        LoggerUtils.info(
                "Deal updated from Bybit position:\n" +
                        "Leverage: " + oldLeverage + " → " + this.leverageUsed + "\n" +
                        "Position Size: " + oldPositionSize + " → " + this.positionSize + "\n" +
                        "Potential Loss: " + oldPotentialLoss + " → " + this.potentialLoss + "\n" +
                        "Entry Price: " + oldEntryPrice + " → " + this.entryPrice + "\n" +
                        "Stop Loss: " + oldStopLoss + " → " + this.stopLoss + "\n" +
                        "ROI: " + roi + "\n"
        );
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
            LoggerUtils.warn("Попытка записать выход для неактивной сделки " + this.id);
            return;
        }
        if (!takeProfits.contains(exitPrice)) {
            LoggerUtils.warn("Попытка записать выход по неизвестному TP (" + exitPrice + ") для сделки " + this.id);
            return;
        }

        ExitStep exit = new ExitStep(exitPrice, exitAmount);
        executedExits.add(exit);
        LoggerUtils.debug("Зарегистрирован выход: цена=" + exitPrice + ", количество=" + exitAmount + " для сделки " + this.id);

        // Если все TP выполнены — сделка считается закрытой
        // Используем >= на случай, если выходов больше, чем TP (например, Market order закрыл всё)
        if (executedExits.size() >= takeProfits.size() && !takeProfits.isEmpty()) {
            this.active = false;
            LoggerUtils.info("Сделка " + this.id + " помечена как неактивная, так как все TP выполнены.");
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
            boolean executed = executedExits.stream().anyMatch(e -> Double.compare(e.exitPrice(), tp) == 0);
            if (!executed) {
                count.incrementAndGet();
            }
        });
        int remaining = count.get();
        LoggerUtils.info("Оставшиеся TP для сделки " + this.id + ": " + remaining);
        return remaining;
    }
    public boolean isPositivePNL() {
        if (positionInfo != null) {
            positivePnL = positionInfo.getUnrealisedPnl() > 0;
            return positivePnL;
        }
        return false;
    }


    @Override
    public String toString() {
        return "🟢\"" + strategyName + "\uD83E\uDDE0\" " + symbol + " " + direction.toString().charAt(0) + " " + entryType +
                "\n EP: " + entryPrice +
                "\n SL: " + stopLoss +
                "\n TP: " + takeProfits + "\n";
    }

    public String bigDealToString() {
        return id + "\n" + this +
                "QTY: " + positionSize + "\n" +
                "Риск: " + getRiscValue();
    }

    private String getRiscValue() {
        if (isPositivePNL()) {
            return " Потерять прибыль";
        }
        if (stopLoss == 0 && direction == Direction.LONG) {
            return positionSize * entryPrice + " $ (не установлен SL)\n";
        }
        if (stopLoss == 0 && direction == Direction.SHORT) {
            return EmojiUtils.ERROR + "НЕ ОГРАНИЧЕН!! (не установлен SL)\n";
        }
        return Math.round(positionSize * Math.abs(entryPrice - stopLoss) * 1000.0) / 1000.0 + " $\n";
    }


    public String addOrderId(OrderManager order) {
        if (order == null) return "order == null";

        String message = "";
        if (order.getOrderType() == OrderManager.OrderType.SL) {
            ordersIdList.removeIf(om -> om.getOrderType() == OrderManager.OrderType.SL);
            ordersIdList.add(order);
            setStopLoss(order.getOrderPrice());
            message = "🔗SL заменен: " + order.getOrderId() + " -> " + order.getOrderPrice() + "\n";
            return message; // ← ВЫХОД
        }
        if (order.getOrderType() == OrderManager.OrderType.TP) {
            if (takeProfits == null) takeProfits = new ArrayList<>();
            takeProfits.add(order.getOrderPrice());
            takeProfits.sort(Double::compareTo);
            message = "🔗 Привязан TP (лимит): " + order.getOrderId() + " -> " + order.getOrderPrice() + "\n";
            ordersIdList.add(order); // ← только здесь
            return message;
        }
        return "Неизвестный тип ордера";
    }


    public List<OrderManager> getOrdersIdList() {
        return Collections.unmodifiableList(ordersIdList);
    }

    public void clearOrdersIdList() {
        this.ordersIdList.clear();
    }
    // === Вспомогательные классы ===


        public record ExitStep(double exitPrice, double exitAmount) {
    }


    /**
     * Внутренний метод поиска orderId по типу.
     */
    public List<String> getOrderIdsByType(OrderManager.OrderType type) {
        if (ordersIdList == null || ordersIdList.isEmpty()) {
            return Collections.emptyList();
        }
        return ordersIdList.stream()
                .filter(order -> order.getOrderType() == type)
                .map(OrderManager::getOrderId)
                .filter(Objects::nonNull) // на всякий случай
                .collect(Collectors.toList());
    }
    // equals и hashCode можно добавить при необходимости, например, для хранения в Set

    public double getRoi() {
        if (leverageUsed == 0 || positionInfo.getPositionValue() == 0) {
            return 0.0;
        }
        double initialMargin = positionInfo.getPositionValue() / leverageUsed;
        if (initialMargin == 0) {
            return  0.0;
        }
        return (positionInfo.getUnrealisedPnl() / initialMargin) * 100.0;
    }
}