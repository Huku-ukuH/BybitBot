package org.example.update;

import org.example.bybit.BybitManager;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.deal.ActiveDealStore;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.model.Symbol;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.strategies.strategies.superStrategy.AbstractStrategy;
import org.example.strategy.strategies.strategies.StrategyFactory;
import org.example.util.LoggerUtils;

import java.util.List;

public class DealCreator {

    private final DealCalculator dealCalculator;
    public DealCreator(DealCalculator dealCalculator) {
        this.dealCalculator = dealCalculator;
    }

    public CreationResult dealCreationTypeSorter (
            String strategyName,
            ActiveDealStore store,
            long chatId,
            BybitManager bybitManager,
            List<PositionInfo> pendingPositions,
            List<BybitPositionTrackerService.OrderInfo> pendingOrdersForDealCreation,
            int currentIndex, OrderRestorer orderRestorer) {

        if (pendingOrdersForDealCreation.isEmpty()) {
            return createNextDeal(strategyName, store, chatId, bybitManager, pendingPositions, currentIndex, orderRestorer);
        }
        return createNextDealByLimitOrder(strategyName, store, chatId, pendingOrdersForDealCreation, currentIndex);
    }

    public CreationResult createNextDeal(
            String strategyName,
            ActiveDealStore store,
            long chatId,
            BybitManager bybitManager,
            List<PositionInfo> pendingPositions,
            int currentIndex, OrderRestorer orderRestorer) {

        if (currentIndex >= pendingPositions.size()) {
            return new CreationResult(false, "❌ Нет сделок для восстановления.");
        }

        PositionInfo pos = pendingPositions.get(currentIndex);
        currentIndex++;

        if (!StrategyFactory.isStrategyAvailable(strategyName)) {
            return new CreationResult(true, "⚠️ Strategy '" + strategyName + "' не найдена. Доступные: " +
                    String.join(", ", StrategyFactory.getAvailableStrategies()));
        }

        try {
            AbstractStrategy strategy = StrategyFactory.getStrategy(strategyName);
            Deal deal = strategy.createDealByUpdate(pos, chatId, strategyName);
            deal.setId(pos.getSymbol() + "_" + strategyName + "_" + System.currentTimeMillis());

            StringBuilder msg = new StringBuilder(orderRestorer.restoreOrders(deal, bybitManager));


            // Если TP/SL отсутствуют — устанавливаем
            if (deal.getTakeProfits().isEmpty()) {
                strategy.setTP(deal, bybitManager);
            }

            //
            Double currentSL = deal.getStopLoss();
            if (currentSL == null || currentSL <= 0.0) {
                double newSL = dealCalculator.getStopLossForUpdatePosition(deal, strategy.getConfig());
                // Дополнительная защита: если расчёт дал 0 — не ставим
                if (newSL <= 0) {
                    LoggerUtils.warn("Рассчитанный SL <= 0 для " + deal.getSymbol() + ". Пропуск установки.");
                } else {
                    deal.setStopLoss(newSL);
                    msg.append(strategy.setSL(deal, bybitManager));
                }
            }

            store.addDeal(deal);
            msg.append("✅ Deal для ").append(pos.getSymbol()).append(" восстановлена со стратегией '").append(strategyName).append("'.\n");

            if (currentIndex < pendingPositions.size()) {
                PositionInfo next = pendingPositions.get(currentIndex);
                msg.append("\n🆕 След: ").append(next.getSymbol()).append(". Укажите стратегию:");
                return new CreationResult(true, msg.toString(), currentIndex);
            } else {
                return new CreationResult(false, msg.append("\n✅ Все Deals восстановлены!").toString());
            }

        } catch (Exception e) {
            LoggerUtils.error("Ошибка при создании Deal для " + pos.getSymbol(), e);
            return new CreationResult(true, "❌ Ошибка: " + e.getMessage());
        }
    }


    public CreationResult createNextDealByLimitOrder(
            String strategyName,
            ActiveDealStore store,
            long chatId,
            List<BybitPositionTrackerService.OrderInfo> pendingOrdersForDealCreation,
            int currentIndex) {

        if (currentIndex >= pendingOrdersForDealCreation.size()) {
            return new CreationResult(false, "❌ Нет ордеров для создания сделок.");
        }

        BybitPositionTrackerService.OrderInfo orderInfo = pendingOrdersForDealCreation.get(currentIndex);

        Symbol symbol = orderInfo.getSymbol();

        // 🔍 Проверяем, существует ли уже сделка по этому символу
        if (!store.getDealsBySymbol(symbol).isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("⚠️ Сделка для ").append(symbol).append(" уже существует. Пропускаем.\n");

            // Переходим к следующему ордеру
            if (currentIndex + 1 < pendingOrdersForDealCreation.size()) {
                BybitPositionTrackerService.OrderInfo next = pendingOrdersForDealCreation.get(currentIndex + 1);
                msg.append("\n🆕 След: ").append(next.getSymbol()).append(". Укажите стратегию:");
                return new CreationResult(true, msg.toString(), currentIndex + 1);
            } else {
                return new CreationResult(false, msg.append("\n✅ Все ордера обработаны.").toString());
            }
        }

        // Проверка стратегии
        if (!StrategyFactory.isStrategyAvailable(strategyName)) {
            return new CreationResult(true, "⚠️ Strategy '" + strategyName + "' не найдена. Доступные: " +
                    String.join(", ", StrategyFactory.getAvailableStrategies()));
        }

        try {
            AbstractStrategy strategy = StrategyFactory.getStrategy(strategyName);
            Deal deal = strategy.createDealByUpdate(orderInfo, chatId, strategyName);
            deal.setId(symbol + "_" + strategyName + "_" + System.currentTimeMillis());

            store.addDeal(deal);

            StringBuilder msg = new StringBuilder();
            msg.append("✅ Deal для ордера ").append(symbol).append(" создана со стратегией '").append(strategyName).append("'.\n");

            if (currentIndex + 1 < pendingOrdersForDealCreation.size()) {
                BybitPositionTrackerService.OrderInfo nextOrderInfo = pendingOrdersForDealCreation.get(currentIndex + 1);
                msg.append("\n🆕 След: ").append(nextOrderInfo.getSymbol()).append(". Укажите стратегию:");
                return new CreationResult(true, msg.toString(), currentIndex + 1);
            } else {
                return new CreationResult(false, msg.append("\n✅ Все Deals по ордерам восстановлены!").toString());
            }

        } catch (Exception e) {
            LoggerUtils.error("Ошибка при создании Deal по ордеру для " + symbol, e);
            return new CreationResult(true, "❌ Ошибка: " + e.getMessage());
        }
    }

}