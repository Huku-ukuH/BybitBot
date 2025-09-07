package org.example.strategy.strategies;

import org.example.ai.AiService;
import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;
import org.example.strategy.params.ExitPlan;

public interface TradingStrategy {

    /**
     * Возвращает значения настройки поведения сделки
     */
    StrategyConfig getConfig();

    /**
     * Планирует выход из сделки.
     * Возвращает план выхода.
     */
    ExitPlan planExit(Deal deal);
    /**
     * Создание сделки
     */
    Deal createDeal(AiService aiService, String messageText, long chatId, String strategyName);
    DealValidationResult validateDeal(Deal deal, BybitMarketService marketService);
    String calculateDeal(Deal deal, DealCalculator dealCalculator);


    /**
     * Реагирует на обновление цены.
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

    double RiskUpdate(BybitAccountService accountService);

}