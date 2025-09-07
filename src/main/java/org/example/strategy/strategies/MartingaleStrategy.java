package org.example.strategy.strategies;

import org.example.ai.AiService;
import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.dto.DealValidationResult;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MartingaleStrategy implements TradingStrategy {

    @Override
    public StrategyConfig getConfig() {
        return null;
    }

    @Override
    public ExitPlan planExit(Deal deal) {
        return null;
    }

    @Override
    public Deal createDeal(AiService aiService, String messageText, long chatId, String strategyName) {
        return null;
    }

    @Override
    public DealValidationResult validateDeal(Deal deal, BybitMarketService marketService) {
        ArrayList<String> warnings = new ArrayList<>();
        warnings.add("Пример: в данной стратегии проверка не требуется");
        return new DealValidationResult(warnings, Collections.emptyList());
    }

    @Override
    public String calculateDeal(Deal deal, DealCalculator dealCalculator) {
        return "Пример: в данной стратегии рассчет другой";
    }

    @Override
    public void onPriceUpdate(StrategyContext context, TickerResponse price) {

    }

    @Override
    public void onTakeProfitHit(StrategyContext context, double executedPrice) {

    }

    @Override
    public void onStopLossHit(StrategyContext context) {

    }

    @Override
    public double RiskUpdate(BybitAccountService bybitAccountService) {
        return 0.0;
    }
}