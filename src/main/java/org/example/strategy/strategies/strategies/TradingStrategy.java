package org.example.strategy.strategies.strategies;
import org.example.ai.AiService;
import org.example.bybit.dto.TickerResponse;
import org.example.deal.Deal;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;

public interface TradingStrategy {

    StrategyConfig getConfig();
    Deal createDeal(AiService aiService, String messageText, long chatId, String strategyName);
    void onPriceUpdate(StrategyContext context, TickerResponse price);
    void onTakeProfitHit(StrategyContext context, double executedPrice);
    void onStopLossHit(StrategyContext context);
}