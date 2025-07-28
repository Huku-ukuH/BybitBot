
package org.example.strategy;

import org.example.bybit.dto.TickerResponse;
import org.example.deal.Deal;
import org.example.deal.dto.PartialExitPlan;
import org.example.model.Direction;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.util.LoggerUtils;
import org.example.strategy.params.PartialExitPlanner; // Предполагается, что этот класс существует

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Базовая стратегия, реализующая стандартную логику управления сделкой.
 * Использует PartialExitPlanner и параметры из StrategyConfig.
 */
public class BasedStrategy implements TradingStrategy {

    /**
     * Конфигурация для этой стратегии.
     * Использует значения по умолчанию из ValuesUtil.
     */
    private final StrategyConfig config = new StrategyConfig();

    @Override
    public StrategyConfig getConfig() {
        return this.config;
    }

    @Override
    public List<PartialExitPlan> planExit(StrategyContext context) throws StrategyException {
        Deal deal = context.getActiveDeal();
        StrategyConfig config = this.getConfig();
        Map<Integer, int[]> rules = config.getExitRules();

        PartialExitPlanner planner = new PartialExitPlanner();
        PartialExitPlan plan = planner.planExit(deal.getTakeProfits(), rules);

        return Collections.singletonList(plan);
    }

    // В файле org/example/strategy/BasedStrategy.java

// Добавим поле, чтобы отслеживать, выставили ли мы уже TP для определенного уровня PnL
// Это можно сделать по-разному, например, через флаги или проверку существующих TP
// Для простоты примера предположим, что TP выставляются один раз при первом достижении уровня

    @Override
    public void onPriceUpdate(StrategyContext context, TickerResponse price) {
        Deal deal = context.getActiveDeal();
        if (deal == null || !deal.isActive()) {
            return;
        }

        // 1. Получаем символ сделки
        String dealSymbol = deal.getSymbol().toString(); // Предполагаем, что deal.getSymbol() возвращает объект типа Symbol

        // 2. Проверяем, есть ли данные в ответе
        if (price.getResult() == null || price.getResult().getList() == null || price.getResult().getList().isEmpty()) {
            LoggerUtils.logWarn("onPriceUpdate: Получен пустой TickerResponse для сделки " + deal.getId());
            return;
        }

        // 3. Ищем тикер, соответствующий символу сделки
        Double currentPrice = null;
        for (TickerResponse.Ticker ticker : price.getResult().getList()) {
            if (dealSymbol.equals(ticker.getSymbol())) {
                try {
                    currentPrice = Double.parseDouble(ticker.getLastPrice());
                    break; // Нашли нужный символ, выходим из цикла
                } catch (NumberFormatException e) {
                    LoggerUtils.logError("onPriceUpdate: Не удалось преобразовать lastPrice '" + ticker.getLastPrice() + "' в число для символа " + ticker.getSymbol(), e);
                    // Или обрабатываем ошибку другим способом
                }
            }
        }

        // 4. Проверяем, нашли ли мы цену
        if (currentPrice == null) {
            LoggerUtils.logWarn("onPriceUpdate: Цена для символа " + dealSymbol + " не найдена в TickerResponse");
            return; // Или выполняем другую логику, если цена не найдена
        }

        // --- Теперь у вас есть currentPrice как Double ---
        LoggerUtils.logDebug("BasedStrategy.onPriceUpdate: Текущая цена для " + dealSymbol + " = " + currentPrice);
        double entryPrice = deal.getEntryPrice();
        Direction direction = deal.getDirection(); // LONG или SHORT

        if (entryPrice <= 0) { // Защита от деления на ноль или некорректных данных
            LoggerUtils.logWarn("BasedStrategy.onPriceUpdate: Некорректная цена входа для сделки " + deal.getId());
            return;
        }

        // 1. Рассчитываем текущий PnL в процентах
        double pnlPercent;
        if (direction == Direction.LONG) {
            pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100.0;
        } else { // SHORT
            pnlPercent = ((entryPrice - currentPrice) / entryPrice) * 100.0;
        }

        // 2. Определяем, достигли ли мы какого-либо из целевых уровней PnL
        // Предположим, у нас есть заранее определенные уровни (можно также хранить в StrategyConfig)
        double[] targetPnlLevels = {8.0, 15.0, 23.0}; // Уровни PnL в процентах

        // Простая логика: проверяем каждый уровень. В реальности нужно учитывать,
        // что цена может "пробить" сразу несколько уровней, или нужно отслеживать,
        // какие уровни уже были обработаны.
        for (double targetPnl : targetPnlLevels) {
            // Проверяем, "пересекли" ли мы уровень (для LONG - цена выше, для SHORT - ниже)
            boolean levelReached = (direction == Direction.LONG && pnlPercent >= targetPnl) ||
                    (direction == Direction.SHORT && pnlPercent >= targetPnl); // PnL для SHORT тоже считается положительно при убыльке

            // Проверяем, не выставляли ли мы уже TP для этого уровня
            // (это упрощенная проверка, на практике нужно более точно)
            boolean tpAlreadySet = false;
            for (Double existingTp : deal.getTakeProfits()) {
                // Очень грубая проверка, просто для примера
                if (Math.abs(existingTp - currentPrice) < 0.0001) { // Или какой-то другой порог
                    tpAlreadySet = true;
                    break;
                }
            }

            if (levelReached && !tpAlreadySet) {
                // 3. Выставляем TP на текущей цене (или немного хуже, в зависимости от логики)
                // ВАЖНО: Это просто добавляет цену TP в список. Чтобы *фактически выставить*
                // ордер на бирже, нужно вызвать соответствующий метод BybitOrderService.
                // Пока просто добавляем в модель Deal.
                deal.addTakeProfit(currentPrice);
                LoggerUtils.logInfo("BasedStrategy: Достигнут PnL " + String.format("%.2f", pnlPercent) +
                        "%. Установлен TP на уровне " + currentPrice + " для сделки " + deal.getId());

                // --- Дополнительно: можно применить логику PartialExitPlanner ---
                // Если ты хочешь, чтобы при достижении 8% PnL, например, 30% позиции вышло,
                // а при 15% - еще 40%, тебе нужно:
                // 1. Хранить состояние (сколько уже вышло).
                // 2. При достижении уровня PnL, рассчитать, какой процент нужно вывести.
                // 3. Вызвать BybitOrderService.placeTakeProfitOrder(...) с нужным количеством.
                // ---
                // Пример (упрощенный):
                // if (Math.abs(targetPnl - 8.0) < 0.01) {
                //     // Рассчитываем 30% от текущего размера позиции
                //     double qtyToExit = deal.getPositionSize() * 0.30;
                //     // bybitOrderService.placeTakeProfitOrder(deal.getSymbol(), qtyToExit, currentPrice, ...);
                //     LoggerUtils.logInfo("BasedStrategy: Выставлен ордер на выход 30% позиции по TP " + currentPrice);
                // }
                // ---
            }
        }

        // Другая логика onPriceUpdate (например, трейлинг SL) может идти здесь...
        // LoggerUtils.logDebug("BasedStrategy: Получено обновление цены для сделки " + deal.getId() + ". PnL: " + String.format("%.2f", pnlPercent) + "%");
    }

    @Override
    public void onTakeProfitHit(StrategyContext context, double executedPrice) {
        LoggerUtils.logInfo("BasedStrategy: Сработал TP на уровне " + executedPrice + ".");
    }

    @Override
    public void onStopLossHit(StrategyContext context) {
        LoggerUtils.logWarn("BasedStrategy: Сработал SL.");
    }


}