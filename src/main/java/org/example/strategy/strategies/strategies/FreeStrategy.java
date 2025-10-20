package org.example.strategy.strategies.strategies;

import org.example.ai.AiService;
import org.example.bybit.BybitManager;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.strategy.strategies.strategies.superStrategy.AbstractStrategy;
import org.example.update.UpdateManager;
import org.example.deal.dto.DealValidationResult;
import org.example.monitor.dto.PriceUpdate;
import org.example.strategy.config.StrategyConfig;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.params.StopLossManager;

public class FreeStrategy extends AbstractStrategy {
    public FreeStrategy() {
        super();
    }

    @Override
    protected StrategyConfig createConfig() {
        // TODO: своя логика, если требуется
        return null;
    }

    @Override
    public DealValidationResult validateDeal(Deal deal, BybitMarketService marketService) {
        // TODO: своя логика, если требуется
        return null;
    }

    @Override
    public String calculateDeal(Deal deal, DealCalculator dealCalculator) {
        return "МЫ НЕ ХОТИМ СЧИТАТЬ СДЕЛКУ ПРИ ЭТОЙ СТРАТЕГИИ, ВОТ ПРИМЕР ПЕРЕОПРЕДЕЛЕНИЯ: ДЛЯ ЭТОЙ СТРАТЕГИИ СЧЕТ НЕ ТРЕБУЕТСЯ";
    }

    @Override
    public ExitPlan planExit(Deal deal) {
        // TODO: своя логика, если требуется
        return null;
    }

    @Override
    public double RiskUpdate(BybitAccountService bybitAccountService) {
        // TODO: своя логика, если требуется
        return 0.0;
    }

    @Override
    public StrategyConfig getConfig() {
        // TODO: своя логика, если требуется
        return null;
    }

    @Override
    public Deal createDealBySignal(AiService aiService, String messageText, long chatId, String strategyName) {
        // TODO: своя логика, если требуется
        return null;
    }

    @Override
    public void onPriceUpdate(Deal deal, PriceUpdate priceUpdate, UpdateManager updateManager, StopLossManager stopLossManager, BybitManager bybitManager) {
        // TODO: своя логика, если требуется
    }

    @Override
    public void onTakeProfitHit(Deal deal,  double executedPrice) {
        // TODO: своя логика, если требуется
    }

    @Override
    public void onStopLossHit(Deal deal) {
        // TODO: своя логика, если требуется
    }
}