
package org.example.strategy;

import org.example.bybit.dto.TickerResponse;
import org.example.deal.Deal;
import org.example.deal.dto.PartialExitPlan;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.strategy.params.PartialExitPlanner;

import java.util.List;
import java.util.Map;

public interface TradingStrategy {
    StrategyConfig getConfig();

    //Планирует выход(ы) из сделки на основе контекста.
    List<PartialExitPlan> planExit(StrategyContext context) throws StrategyException;

    /**
     * Реагирует на обновление рыночной цены.
     * @param context Контекст с информацией о сделке.
     * @param price   Новые рыночные данные.
     */
    void onPriceUpdate(StrategyContext context, TickerResponse price);


    void onTakeProfitHit(StrategyContext context, double executedPrice);

    //Реагирует на срабатывание Stop Loss.
    void onStopLossHit(StrategyContext context);


    default Map<Integer, int[]> getExitRules() {
        // Дефолтная реализация - возвращает стандартные правила
        return PartialExitPlanner.getDefaultExitRules();
    }
}