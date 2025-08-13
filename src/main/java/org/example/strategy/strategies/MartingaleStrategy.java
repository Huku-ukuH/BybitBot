package org.example.strategy.strategies;

import org.example.bybit.dto.TickerResponse;
import org.example.deal.dto.PartialExitPlan;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.dto.StrategyContext;

import java.util.List;

public class MartingaleStrategy implements TradingStrategy {

    @Override
    public StrategyConfig getConfig() {
        return null;
    }

    @Override
    public List<PartialExitPlan> planExit(StrategyContext context) throws StrategyException {
        return List.of();
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
}