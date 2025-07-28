
package org.example.deal;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.model.Direction;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;
import org.example.strategy.config.StrategyConfig; // <-- НОВЫЙ ИМПОРТ
import org.example.strategy.TradingStrategy;   // <-- НОВЫЙ ИМПОРТ

public class DealCalculator {

    private final BybitMarketService bybitMarketService;
    private final BybitAccountService accountService;

    // Конструктор теперь принимает только сервисы
    public DealCalculator(BybitAccountService accountService, BybitMarketService bybitMarketService) {
        this.bybitMarketService = bybitMarketService;
        this.accountService = accountService;
        // strategyConfig больше не передается и не хранится
    }

    // Метод calculate теперь получает Deal и извлекает из него конфиг
    public String calculate(Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        // this.deal = deal; // Больше не сохраняем deal как поле

        // --- НОВОЕ: Получаем конфиг из стратегии сделки ---
        StrategyConfig strategyConfig;
        try {
            TradingStrategy strategy = deal.getStrategy();
            if (strategy == null) {
                throw new IllegalStateException("Стратегия не установлена для сделки " + deal.getId());
            }
            strategyConfig = strategy.getConfig();
            if (strategyConfig == null) {
                // Можно использовать ValuesUtil как фолбэк или бросить исключение
                LoggerUtils.logWarn("Конфиг стратегии равен null для сделки " + deal.getId() + ". Используются значения по умолчанию.");
                strategyConfig = new StrategyConfig(); // Используем конфиг по умолчанию
                // throw new IllegalStateException("Конфиг стратегии равен null для сделки " + deal.getId());
            }
        } catch (Exception e) { // Ловим RuntimeException от deal.getStrategy() или IllegalStateException
            LoggerUtils.logError("Ошибка получения конфига стратегии для сделки " + deal.getId(), e);
            // Можно использовать ValuesUtil как фолбэк или бросить исключение
            strategyConfig = new StrategyConfig(); // Фолбэк
            // throw new StrategyException("Не удалось получить конфиг стратегии", e); // Если создадите StrategyException
        }
        // ----------------------------------------------------

        // 1. Entry price
        double entryPrice = deal.getEntryPrice() != null && deal.getEntryPrice() > 0
                ? deal.getEntryPrice()
                : bybitMarketService.getLastPrice(deal.getSymbol().toString());
        deal.setEntryPrice(entryPrice);
        LoggerUtils.logInfo("цена " + deal.getSymbol() + " = " + entryPrice);

        // 2. Stop Loss - теперь используем параметр из strategyConfig
        double stopLoss = deal.getStopLoss() != null && deal.getStopLoss() > 0
                ? deal.getStopLoss()
                : getDefaultStopLoss(deal, strategyConfig); // <-- Передаем deal и config
        deal.setStopLoss(stopLoss);
        LoggerUtils.logInfo("SL " + deal.getSymbol() + " = " + stopLoss);

        // 3. Position size (и проверка minQty внутри)
        double positionSize = calculatePositionSize(deal, strategyConfig, bybitMarketService); // <-- Передаем deal, config и сервис
        deal.setPositionSize(positionSize);
        LoggerUtils.logInfo("размер позиции " + deal.getSymbol() + " = " + positionSize);


        // 4. Leverage - теперь используем параметр из strategyConfig
        int leverageUsed = findValidLeverage(deal, strategyConfig, bybitMarketService); // <-- Передаем deal, config и сервис
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

        // 7. Потенциальный убыток
        double delta = Math.abs(entryPrice - stopLoss);
        double potentialLoss = positionSize * delta;
        potentialLoss = Math.round(potentialLoss * 1000.0) / 1000.0;
        // deal.setPotencialLoss(potentialLoss); // Возможно, опечатка в "Potencial"?
        // Предполагая, что поле в Deal называется potencialLoss:
        deal.setPotentialLoss(potentialLoss);

        return String.format(
                "Размер позиции: %.2f\nСтоп-лосс: %.5f\nПлечо: %dx\nНеобходимый капитал: %.2f USDT",
                positionSize, deal.getStopLoss(), leverageUsed, requiredCapital
        );
    }

    // Методы теперь принимают deal и strategyConfig как параметры

    // Используем параметр из strategyConfig
    private double getDefaultStopLoss(Deal deal, StrategyConfig strategyConfig) {
        // Теперь получаем АБСОЛЮТНОЕ значение отклонения SL в долларах из переданного конфига
        double slAbsoluteValue = strategyConfig.getDefaultSlPercent(); // Несмотря на название, это сумма в USD

        if (deal.getDirection() == Direction.LONG) {
            // Для LONG SL должен быть НИЖЕ цены входа
            return deal.getEntryPrice() - slAbsoluteValue;
        } else { // SHORT
            // Для SHORT SL должен быть ВЫШЕ цены входа
            return deal.getEntryPrice() + slAbsoluteValue;
        }
    }

    // Используем параметр из strategyConfig
    private double calculatePositionSize(Deal deal, StrategyConfig strategyConfig, BybitMarketService bybitMarketService) {
        double delta = Math.abs(deal.getEntryPrice() - deal.getStopLoss());
        if (delta == 0) {
            throw new IllegalArgumentException("\nSL == entryPrice (деление на ноль!)");
        }

        // Теперь получаем максимальный убыток из переданного конфига
        double maxLoss = strategyConfig.getMaxLossInPosition();
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
            deal.setMinQty(true);
            roundedSize = minQty;
        }

        return roundedSize;
    }

    // Используем параметр из strategyConfig
    private int findValidLeverage(Deal deal, StrategyConfig strategyConfig, BybitMarketService bybitMarketService) {
        // Теперь получаем массив плеч из переданного конфига
        int[] leverageOptions = strategyConfig.getLeverageTrails();
        for (int leverage : leverageOptions) {
            if (isLeverageAcceptable(deal.getEntryPrice(), deal.getPositionSize(), leverage, bybitMarketService)) {
                return leverage;
            }
        }
        return 1; // Минимальное плечо, если ничего не подошло
    }

    // Этот метод тоже адаптируем, передавая сервисы, если нужно
    // (В данном случае сервисы не используются внутри, но передаются для единообразия или если логика усложнится)
    private boolean isLeverageAcceptable(Double entryPrice, double positionSize, int leverage, BybitMarketService bybitMarketService) {
        // Эта логика не зависит от конфига стратегии, она чисто математическая
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
}