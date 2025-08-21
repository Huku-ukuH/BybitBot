package org.example.strategy.strategies;

import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.deal.Deal;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.strategy.params.ExitPlan;

public interface TradingStrategy {
    StrategyConfig getConfig();

    /**
     * Планирует выход из сделки.
     * Возвращает единый план выхода (TP, PnL, trailing).
     */
    ExitPlan planExit(Deal deal);

    /**
     * Реагирует на обновление рыночной цены.
     */
    void onPriceUpdate(StrategyContext context, TickerResponse price);

    /**
     * Реагирует на срабатывание Take Profit.
     */
    void onTakeProfitHit(StrategyContext context, double executedPrice);

    /**
     * Реагирует на срабатывание Stop Loss.
     */
    void onStopLossHit(StrategyContext context);

    double lossUpdate(BybitAccountService accountService);

}