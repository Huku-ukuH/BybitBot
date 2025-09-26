
package org.example.deal;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.model.Direction;
import org.example.util.LoggerUtils;
import org.example.util.MathUtils;
import org.example.util.ValidationUtils;
import org.example.strategy.config.StrategyConfig; // <-- НОВЫЙ ИМПОРТ
import org.example.strategy.strategies.strategies.TradingStrategy;   // <-- НОВЫЙ ИМПОРТ

public class DealCalculator {

    private final BybitMarketService bybitMarketService;
    private final BybitAccountService accountService;


    public DealCalculator(BybitAccountService accountService, BybitMarketService bybitMarketService) {
        this.bybitMarketService = bybitMarketService;
        this.accountService = accountService;
    }

    public String calculate(Deal deal) {
        LoggerUtils.debug("DealCalculator calculate - Начался рассчет " + deal.getSymbol());
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        StrategyConfig strategyConfig;
        try {
            TradingStrategy strategy = deal.getStrategy();
            if (strategy == null) {
                throw new IllegalStateException("Стратегия не установлена для сделки " + deal.getId());
            }
            strategyConfig = strategy.getConfig();
            if (strategyConfig == null) {
                strategyConfig = new StrategyConfig(); // если конфиг null создать умолчание
            }
        } catch (Exception e) {
            LoggerUtils.error("Ошибка получения конфига стратегии для сделки " + deal.getId(), e);
            strategyConfig = new StrategyConfig();
        }

        // 1. Stop Loss - используем параметр из strategyConfig
        double stopLoss = deal.getStopLoss() != null && deal.getStopLoss() > 0
                ? deal.getStopLoss()
                : getDefaultStopLoss(deal, strategyConfig);
        deal.setStopLoss(MathUtils.formatPrice(deal.getEntryPrice(), stopLoss));
        LoggerUtils.info("SL " + deal.getSymbol() + " = " + stopLoss);


        // 2. Position size (и проверка minQty внутри)
        double actualBalance = fetchBalance();
        double positionSize = calculatePositionSize(deal, strategyConfig, bybitMarketService, actualBalance); // <-- Передаем deal, config и сервис
        deal.setPositionSize(positionSize);

        // 3. Leverage - теперь используем параметр из strategyConfig
        int leverageUsed = findValidLeverage(deal, strategyConfig, actualBalance); // <-- Передаем deal, config и сервис
        deal.setLeverageUsed(leverageUsed);

        // 4. Required capital
        double requiredCapital = calculateRequiredCapital(deal);
        deal.setRequiredCapital(requiredCapital);

        // 5. Проверка баланса
        if (requiredCapital > actualBalance) {
            throw new IllegalStateException("\nНедостаточно средств. Нужно: " + requiredCapital + ", доступно: " + actualBalance);
        }

        LoggerUtils.debug("DealCalculator calculate - Закончился рассчет " + deal.getSymbol());
        return "QTY: " + MathUtils.formatPrice(0.01, positionSize) + "\n" +
                "SL: " + MathUtils.formatPrice(deal.getEntryPrice(), deal.getStopLoss()) + "\n" +
                "LV: " + leverageUsed + "x\n" +
                "ReqCap: " + MathUtils.formatPrice(0.01, requiredCapital) + " USDT\n" +
                "Balance: " + MathUtils.formatPrice(0.01, actualBalance) + " USDT";
    }


    public double getDefaultStopLoss(Deal deal, StrategyConfig strategyConfig) {
        double entryPrice = deal.getEntryPrice();
        double slPercent = strategyConfig.getDefaultSlPercent(); // например, 0.20 → 20%
        LoggerUtils.debug("getDefaultStopLoss Расчёт дефолтного SL:");
        double stopLoss;
        if (deal.getDirection() == Direction.LONG) {
            stopLoss = entryPrice * (1 - slPercent);
        } else {
            stopLoss = entryPrice * (1 + slPercent);
        }
        return stopLoss;
    }

    public double getStopLossForUpdatePosition(Deal deal, StrategyConfig strategyConfig) {
        // 1. Получаем данные
        double entryPrice = deal.getEntryPrice();
        double positionSize = deal.getPositionSize();
        Direction direction = deal.getDirection();
        double balance = accountService.getUsdtBalance();

        // 2. Валидация
        if (positionSize <= 0) {
            throw new IllegalArgumentException("Размер позиции должен быть > 0");
        }
        if (entryPrice <= 0) {
            throw new IllegalArgumentException("Цена входа некорректна: " + entryPrice);
        }

        // 3. Рассчитываем допустимый убыток в USD
        double maxLossPercent = strategyConfig.getMaxLossPrecen(); // например, 1.0 = 1%
        double maxLossUSD = balance * (maxLossPercent / 100.0);

        // 4. Рассчитываем максимально допустимое расстояние до стопа
        double delta = maxLossUSD / positionSize;

        // 5. Определяем стоп в зависимости от направления
        double stopLoss;
        if (direction == Direction.LONG) {
            stopLoss = entryPrice - delta;
        } else if (direction == Direction.SHORT) {
            stopLoss = entryPrice + delta;
        } else {
            throw new IllegalStateException("Неизвестное направление: " + direction);
        }

        // 6. Округляем по шагу цены (если нужно)
        stopLoss = MathUtils.formatPrice(deal.getEntryPrice(), stopLoss);

        LoggerUtils.debug(
                "Рассчитан стоп-лосс для обновления: " +
                        "entry=" + entryPrice + ", posSize=" + positionSize +
                        ", maxLoss%=" + maxLossPercent + ", maxLossUSD=" + maxLossUSD +
                        ", delta=" + delta + ", stopLoss=" + stopLoss
        );

        return stopLoss;
    }

    // Используем параметр из strategyConfig
    private double calculatePositionSize(Deal deal, StrategyConfig strategyConfig, BybitMarketService bybitMarketService, double balance) {
        LoggerUtils.debug("calculatePositionSize 🧮 Начало расчёта размера позиции");

        double delta = Math.abs(deal.getEntryPrice() - deal.getStopLoss());
        if (delta == 0) {
            LoggerUtils.info("❌❌❌❌❌ SL совпадает с ценой входа — деление на ноль!❌❌❌❌❌");
            throw new IllegalArgumentException("SL == entryPrice (деление на ноль!)");
        }

        double maxLossPercent = strategyConfig.getMaxLossPrecen(); // например, 1.0 → 1%
        double maxLossUSD = balance * (maxLossPercent / 100.0);
        double rawPositionSize = maxLossUSD / delta;
        double potentialLoss = rawPositionSize * delta;

        // Коррекция, если потенциальный убыток превышает лимит
        if (potentialLoss > maxLossUSD) {
            rawPositionSize = maxLossUSD / delta;
            LoggerUtils.info("potentialLoss > maxLossUSD \nКоррекция rawPositionSize = " + rawPositionSize);
        }

        // Округление по шагу лота
        double minQty = bybitMarketService.getMinOrderQty(deal.getSymbol().toString());
        double roundedSize = bybitMarketService.roundLotSize(deal.getSymbol().toString(), rawPositionSize);

        // Проверка minQty
        if (roundedSize < minQty) {
            LoggerUtils.warn("устанавливаем = minQty в размер позиции");
            roundedSize = minQty;
        }

        LoggerUtils.info("📊 РАСЧЁТ РАЗМЕРА ПОЗИЦИИ (итог)" +
                "\nМакс. риск: " + maxLossPercent +
                "%\nРазмер позиции: " + roundedSize +
                "\nПотенциальный убыток: " + potentialLoss + " USDT");
        // =============================================
        return roundedSize;
    }

    // Используем параметр из strategyConfig
    private int findValidLeverage(Deal deal, StrategyConfig strategyConfig, double balance) {

        int[] leverageOptions = strategyConfig.getLeverageTrails();
        for (int leverage : leverageOptions) {
            if (isLeverageAcceptable(deal.getEntryPrice(), deal.getPositionSize(), leverage, balance)) {
                return leverage;
            }
        }
        return 3;
    }


    private boolean isLeverageAcceptable(Double entryPrice, double positionSize, int leverage, double balance) {
        double requiredCapital = (positionSize * entryPrice) / leverage;
        // Требуемый капитал должен быть <= 50% от баланса (или другого лимита)
        return requiredCapital > 0 && requiredCapital <= balance * 0.5;
    }

    private double calculateRequiredCapital(Deal deal) {
        return (deal.getPositionSize() * deal.getEntryPrice()) / deal.getLeverageUsed();
    }

    private double fetchBalance() {
        return accountService.getUsdtBalance();
    }


    public double calculateExitQty(Deal deal, int exitPercentage) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        if (exitPercentage <= 0 || exitPercentage > 100) {
            throw new IllegalArgumentException("Exit percentage must be between 1 and 100. Got: " + exitPercentage);
        }

        // 1. Рассчитываем "сырой" объём
        double rawQty = deal.getPositionSize() * exitPercentage / 100.0;
        LoggerUtils.debug("DealCalculator.calculateExitQty: rawQty = " + rawQty + " (positionSize=" + deal.getPositionSize() + ", %=" + exitPercentage + ")");

        // 2. Округляем по шагу лота
        double roundedQty = bybitMarketService.roundLotSize(deal.getSymbol().toString(), rawQty);
        LoggerUtils.debug("DealCalculator.calculateExitQty: roundedQty = " + roundedQty);

        // 3. Проверяем minQty
        double minQty = bybitMarketService.getMinOrderQty(deal.getSymbol().toString());
        if (roundedQty < minQty) {
            LoggerUtils.debug("DealCalculator.calculateExitQty: roundedQty (" + roundedQty + ") < minQty (" + minQty + ") → return 0.0");
            return 0.0;
        }

        // 4. Защита: не выходим больше, чем есть
        if (roundedQty > deal.getPositionSize()) {
            LoggerUtils.warn("DealCalculator.calculateExitQty: exit qty (" + roundedQty + ") > position size (" + deal.getPositionSize() + ") → return 0.0");
            return 0.0;
        }

        LoggerUtils.debug("DealCalculator.calculateExitQty: exit qty = " + roundedQty);
        return roundedQty;
    }
}