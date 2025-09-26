package org.example.strategy.strategies.strategies;
import org.example.ai.AiService;
import org.example.deal.Deal;
import org.example.deal.UpdateManager;
import org.example.monitor.dto.PositionInfo;
import org.example.monitor.dto.PriceUpdate;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.params.StopLossManager;

import java.io.IOException;


public interface TradingStrategy {

    StrategyConfig getConfig();
    Deal createDeal(AiService aiService, String messageText, long chatId, String strategyName);
    Deal createDeal(PositionInfo positionInfo, long chatId, String strategyName);
    void onPriceUpdate(Deal deal, PriceUpdate priceUpdate, UpdateManager updateManager, StopLossManager stopLossManager) throws IOException;
    void onTakeProfitHit(Deal deal, double executedPrice);
    void onStopLossHit(Deal deal);
}