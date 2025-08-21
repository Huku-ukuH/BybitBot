
package org.example.deal;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.model.Direction;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;
import org.example.strategy.config.StrategyConfig; // <-- НОВЫЙ ИМПОРТ
import org.example.strategy.strategies.TradingStrategy;   // <-- НОВЫЙ ИМПОРТ

public class DealCalculator {

    private final BybitMarketService bybitMarketService;
    private final BybitAccountService accountService;


    public DealCalculator(BybitAccountService accountService, BybitMarketService bybitMarketService) {
        this.bybitMarketService = bybitMarketService;
        this.accountService = accountService;
    }

    public String calculate(Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        StrategyConfig strategyConfig;
        try {
            TradingStrategy strategy = deal.getStrategy();
            if (strategy == null) {
                throw new IllegalStateException("Стратегия не установлена для сделки " + deal.getId());
            }
            strategyConfig = strategy.getConfig();
            if (strategyConfig == null) {
                LoggerUtils.logWarn("Конфиг стратегии равен null для сделки " + deal.getId() + ". Используются значения по умолчанию.");
                strategyConfig = new StrategyConfig(); // если конфиг null создать умолчание
            }
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка получения конфига стратегии для сделки " + deal.getId(), e);
            strategyConfig = new StrategyConfig();
        }

        // 1. Entry price
        double entryPrice = deal.getEntryPrice() != null && deal.getEntryPrice() > 0
                ? deal.getEntryPrice()
                : bybitMarketService.getLastPrice(deal.getSymbol().toString());
        deal.setEntryPrice(entryPrice);
        LoggerUtils.logInfo("Текущая цена " + deal.getSymbol() + " = " + entryPrice);

        // 2. Stop Loss - теперь используем параметр из strategyConfig
        double stopLoss = deal.getStopLoss() != null && deal.getStopLoss() > 0
                ? deal.getStopLoss()
                : getDefaultStopLoss(deal, strategyConfig);
        deal.setStopLoss(stopLoss);
        LoggerUtils.logInfo("SL " + deal.getSymbol() + " = " + stopLoss);

        // 3. Position size (и проверка minQty внутри)
        double positionSize = calculatePositionSize(deal, strategyConfig, bybitMarketService); // <-- Передаем deal, config и сервис
        deal.setPositionSize(positionSize);
        LoggerUtils.logInfo("размер позиции " + deal.getSymbol() + " = " + positionSize);


        // 4. Leverage - теперь используем параметр из strategyConfig
        int leverageUsed = findValidLeverage(deal, strategyConfig); // <-- Передаем deal, config и сервис
        deal.setLeverageUsed(leverageUsed);
        LoggerUtils.logInfo("\n" + getClass().getName() + ".findValidLeverage: leverageUsed = " + leverageUsed);

        // 5. Required capital
        double requiredCapital = calculateRequiredCapital(deal);
        deal.setRequiredCapital(requiredCapital);

        // 6. Проверка баланса
        double actualBalance = fetchBalance();
        if (requiredCapital > actualBalance) {
            throw new IllegalStateException("\nНедостаточно средств. Нужно: " + requiredCapital + ", доступно: " + actualBalance);
        }

        return String.format(
                "Размер позиции: %.2f\nСтоп-лосс: %.5f\nПлечо: %dx\nНеобходимый капитал: %.2f USDT",
                positionSize, deal.getStopLoss(), leverageUsed, requiredCapital
        );
    }


    // Методы теперь принимают deal и strategyConfig как параметры


    private double getDefaultStopLoss(Deal deal, StrategyConfig strategyConfig) {
        // Получаем процент отклонения SL из конфига
        double slPercent = strategyConfig.getDefaultSlPercent(); // 0.10 для 0.10%

        if (deal.getDirection() == Direction.LONG) {
            // SL = EntryPrice * (1 - slPercent / 100)
            return deal.getEntryPrice() * (1 - slPercent / 100.0);
        } else { // SHORT
            // SL = EntryPrice * (1 + slPercent / 100)
            return deal.getEntryPrice() * (1 + slPercent / 100.0);
        }
    }

    // Используем параметр из strategyConfig
    private double calculatePositionSize(Deal deal, StrategyConfig strategyConfig, BybitMarketService bybitMarketService) {
        double delta = Math.abs(deal.getEntryPrice() - deal.getStopLoss());
        if (delta == 0) {
            throw new IllegalArgumentException("\nSL == entryPrice (деление на ноль!)");
        }

        // Теперь получаем максимальный убыток из переданного конфига
        double maxLoss = strategyConfig.getMaxLossPrecentInPosition();
        double rawPositionSize = maxLoss / delta; // Используем maxLoss из конфига
        double potentialLoss = rawPositionSize * delta;

        if (potentialLoss > maxLoss) {
            LoggerUtils.logInfo(getClass().getName() + ": potentialLoss(" + potentialLoss + ") > maxLossInPosition(" + maxLoss + "), применяем сокращение.");
            rawPositionSize = maxLoss / delta;
        }

        LoggerUtils.logInfo(getClass().getName() + ": rawPositionSize = " + rawPositionSize + ", potentialLoss = " + (rawPositionSize * delta));

        double minQty = bybitMarketService.getMinOrderQty(deal.getSymbol().toString());
        double roundedSize = bybitMarketService.roundLotSize(deal.getSymbol().toString(), rawPositionSize);

        if (roundedSize < minQty) {
            LoggerUtils.logWarn("Округлённый объём меньше minQty. Устанавливаем позицию = minQty.");
            roundedSize = minQty;
        }

        return roundedSize;
    }

    // Используем параметр из strategyConfig
    private int findValidLeverage(Deal deal, StrategyConfig strategyConfig) {

        int[] leverageOptions = strategyConfig.getLeverageTrails();
        for (int leverage : leverageOptions) {
            if (isLeverageAcceptable(deal.getEntryPrice(), deal.getPositionSize(), leverage)) {
                return leverage;
            }
        }
        return 3;
    }

    // Этот метод тоже адаптируем, передавая сервисы, если нужно
    // (В данном случае сервисы не используются внутри, но передаются для единообразия или если логика усложнится)
    private boolean isLeverageAcceptable(Double entryPrice, double positionSize, int leverage) {

        double requiredCapital = (positionSize * entryPrice) / leverage;
        if (requiredCapital <= 0) {
            LoggerUtils.logInfo("\n" + getClass().getName() + ".isLeverageAcceptable: requiredCapital = " + requiredCapital);
            return false;
        }
        return true;
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
        LoggerUtils.logDebug("DealCalculator.calculateExitQty: rawQty = " + rawQty + " (positionSize=" + deal.getPositionSize() + ", %=" + exitPercentage + ")");

        // 2. Округляем по шагу лота
        double roundedQty = bybitMarketService.roundLotSize(deal.getSymbol().toString(), rawQty);
        LoggerUtils.logDebug("DealCalculator.calculateExitQty: roundedQty = " + roundedQty);

        // 3. Проверяем minQty
        double minQty = bybitMarketService.getMinOrderQty(deal.getSymbol().toString());
        if (roundedQty < minQty) {
            LoggerUtils.logDebug("DealCalculator.calculateExitQty: roundedQty (" + roundedQty + ") < minQty (" + minQty + ") → return 0.0");
            return 0.0;
        }

        // 4. Защита: не выходим больше, чем есть
        if (roundedQty > deal.getPositionSize()) {
            LoggerUtils.logWarn("DealCalculator.calculateExitQty: exit qty (" + roundedQty + ") > position size (" + deal.getPositionSize() + ") → return 0.0");
            return 0.0;
        }

        LoggerUtils.logDebug("DealCalculator.calculateExitQty: exit qty = " + roundedQty);
        return roundedQty;
    }
}