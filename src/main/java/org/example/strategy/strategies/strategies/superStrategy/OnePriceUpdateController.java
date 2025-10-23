package org.example.strategy.strategies.strategies.superStrategy;

import org.example.bybit.BybitManager;
import org.example.deal.Deal;
import org.example.model.Direction;
import org.example.monitor.dto.PriceUpdate;

import org.example.strategy.params.StopLossManager;
import org.example.update.UpdateManager;

import org.example.util.LoggerUtils;

import java.util.List;
import java.util.Map;

public class OnePriceUpdateController {

    public void handlePriceUpdate(Deal deal, PriceUpdate priceUpdate, UpdateManager updateManager,
                                  StopLossManager stopLossManager, BybitManager bybitManager) {
        if (deal == null || priceUpdate == null) {
            LoggerUtils.warn("OnePriceUpdateController: deal или priceUpdate равны null");
            return;
        }

        if (!deal.isActive()) {
            tryActivateDeal(deal, priceUpdate);
            tryCloseOnFirstTpIfInactive(deal, priceUpdate, bybitManager);
            return;
        }
        updatePnLAndApplyExitRules(deal, priceUpdate, updateManager, stopLossManager);
    }

    private void tryActivateDeal(Deal deal, PriceUpdate priceUpdate) {
        double entryPrice = deal.getEntryPrice();
        double currentPrice = priceUpdate.getPrice();
        Direction direction = deal.getDirection();

        if (entryPrice <= 0) {
            LoggerUtils.warn("Некорректная цена входа для сделки " + deal.getId());
            return;
        }

        boolean crossedEntry = false;
        if (direction == Direction.LONG) {
            crossedEntry = currentPrice >= entryPrice;
        } else if (direction == Direction.SHORT) {
            crossedEntry = currentPrice <= entryPrice;
        }

        if (crossedEntry) {
            deal.setActive(true);
            LoggerUtils.info("Сделка " + deal.getId() + " активирована: цена " + currentPrice + " пересекла entry " + entryPrice);
        }
    }

    private void tryCloseOnFirstTpIfInactive(Deal deal, PriceUpdate priceUpdate, BybitManager bybitManager) {
        List<Double> takeProfits = deal.getTakeProfits();
        if (takeProfits == null || takeProfits.isEmpty()) {
            return;
        }

        double firstTp = takeProfits.get(0);
        double currentPrice = priceUpdate.getPrice();
        Direction direction = deal.getDirection();

        boolean crossedTp = false;
        if (direction == Direction.LONG) {
            crossedTp = currentPrice >= firstTp;
        } else if (direction == Direction.SHORT) {
            crossedTp = currentPrice <= firstTp;
        }

        if (crossedTp) {
            LoggerUtils.info("Цена пересекла первый тейк-профит (" + firstTp + ") при неактивной сделке " + deal.getId() + " — закрываем принудительно.");
            bybitManager.getBybitOrderService().closeDeal(deal);
        }
    }

    private void updatePnLAndApplyExitRules(Deal deal, PriceUpdate priceUpdate, UpdateManager updateManager, StopLossManager stopLossManager) {
        double entryPrice = deal.getEntryPrice();
        Direction direction = deal.getDirection();
        double currentPrice = priceUpdate.getPrice();

        if (entryPrice <= 0) {
            LoggerUtils.warn("onPriceUpdate: Некорректная цена входа для сделки " + deal.getId());
            return;
        }

        double pnlPercent;
        if (direction == Direction.LONG) {
            pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        } else {
            pnlPercent = ((entryPrice - currentPrice) / entryPrice) * 100.0 * deal.getLeverageUsed();
        }

        deal.setPositivePnL(pnlPercent > 0);
        LoggerUtils.debug(deal.getStrategyName() + "-" + deal.getSymbol() + ": PnL = " + String.format("%.2f", pnlPercent) + "%");

        AbstractStrategy strategy = deal.getStrategy();
        if (strategy == null) {
            LoggerUtils.warn("Не удалось получить стратегию для сделки " + deal.getId());
            return;
        }

        Map<Double, Integer> pnlRules = strategy.getConfig().getPnlTpExitRules();
        if (pnlRules == null || pnlRules.isEmpty()) {
            return;
        }

        for (Map.Entry<Double, Integer> rule : pnlRules.entrySet()) {
            double targetPnlLevel = rule.getKey() * deal.getLeverageUsed();
            boolean levelReached = (direction == Direction.LONG && pnlPercent >= targetPnlLevel) ||
                    (direction == Direction.SHORT && pnlPercent >= targetPnlLevel);

            if (levelReached) {
                deal.updateDealFromBybitPosition(
                        updateManager.updateOneDeal(deal.getSymbol().toString())
                );

                if (!deal.getTakeProfits().isEmpty()) {
                    double lastSize = deal.getPositionSize();
                    // updateOneDeal уже обновил размер
                    double newSize = deal.getPositionSize();
                    deal.recordExit(deal.getTakeProfits().get(0), lastSize - newSize);
                }

                LoggerUtils.info("Достигнут PnL " + String.format("%.2f", targetPnlLevel) + "%. Применено правило выхода.");

                moveStopLossToBreakeven(deal, stopLossManager);
            }
        }
    }

    private void moveStopLossToBreakeven(Deal deal, StopLossManager stopLossManager) {
        Direction direction = deal.getDirection();
        double entryPrice = deal.getEntryPrice();
        Double currentSL = deal.getStopLoss();

        boolean shouldMoveToBE = false;
        if (direction == Direction.LONG && currentSL != null && currentSL < entryPrice) {
            shouldMoveToBE = true;
        } else if (direction == Direction.SHORT && currentSL != null && currentSL > entryPrice) {
            shouldMoveToBE = true;
        }

        if (shouldMoveToBE) {
            stopLossManager.moveStopLoss(deal, entryPrice);
            deal.setPositivePnL(true);
            LoggerUtils.info("Сделка " + deal.getId() + " переведена в Breakeven (SL = EP).");
        }
    }
}