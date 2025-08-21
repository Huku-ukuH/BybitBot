package org.example.strategy.strategies;

import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitAccountService;
import org.example.deal.Deal;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;

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
    public void onPriceUpdate(StrategyContext context, TickerResponse price) {

    }

    @Override
    public void onTakeProfitHit(StrategyContext context, double executedPrice) {

    }

    @Override
    public void onStopLossHit(StrategyContext context) {

    }

    @Override
    public double lossUpdate(BybitAccountService bybitAccountService) {
        return 0.0;
    }
}