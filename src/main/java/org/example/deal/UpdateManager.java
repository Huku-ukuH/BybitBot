package org.example.deal;

import lombok.RequiredArgsConstructor;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.strategies.strategies.StrategyFactory;
import org.example.util.LoggerUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для синхронизации состояния сделок в приложении с данными на бирже Bybit.
 * Восстанавливает сделки после перезапуска, привязывает orderId ордеров (TP/SL),
 * обновляет текущее состояние позиций.
 * <p>
 * Не взаимодействует с пользователем напрямую — только логирует и возвращает результат.
 */
@Component
@RequiredArgsConstructor
public class UpdateManager {

    private final ActiveDealStore activeDealStore;
    private final BybitPositionTrackerService positionTrackerService;

    /**
     * Основной метод. Синхронизирует сделки в памяти с состоянием на Bybit.
     *
     * @return результат синхронизации: сколько сделок обновлено, создано, удалено
     */
    public SyncResult syncWithExchange() {
        try {
            List<PositionInfo> exchangePositions = positionTrackerService.getPositionList();
            LoggerUtils.logInfo("🔄 Найдено " + exchangePositions.size() + " активных позиций на Bybit");

            SyncResult result = new SyncResult();

            // 1. Обновляем существующие сделки
            for (Deal deal : new ArrayList<>(activeDealStore.getAllDeals())) {
                PositionInfo positionOnExchange = findPosition(exchangePositions, deal.getSymbol().toString());
                if (positionOnExchange == null) {
                    handleClosedPosition(deal, result);
                } else {
                    updateExistingDeal(deal, positionOnExchange, result);
                    // Удаляем из списка, чтобы не создавать дубли
                    exchangePositions.remove(positionOnExchange);
                }
            }

            // 2. Создаём новые сделки из оставшихся позиций
            createNewDealsFromPositions(exchangePositions, result);

            return result;
        } catch (Exception e) {
            LoggerUtils.logError("🚨 Ошибка при синхронизации сделок с биржей", e);
            return SyncResult.failed(e);
        }
    }

    private PositionInfo findPosition(List<PositionInfo> positions, String symbol) {
        return positions.stream()
                .filter(p -> symbol.equals(p.getSymbol()))
                .findFirst()
                .orElse(null);
    }

    private void handleClosedPosition(Deal deal, SyncResult result) {
        LoggerUtils.logInfo("🗑️ Позиция " + deal.getSymbol() + " закрыта на бирже");
        activeDealStore.removeDeal(deal.getId());
        result.removed++;
    }

    private void updateExistingDeal(Deal deal, PositionInfo position, SyncResult result) {
        try {
            deal.updateDealFromBybitPosition(position);
            restoreOrderIds(deal, position.getSymbol());
            LoggerUtils.logDebug("✅ Сделка " + deal.getId() + " обновлена из данных Bybit");
            result.updated++;
        } catch (Exception e) {
            LoggerUtils.logError("❌ Ошибка обновления сделки " + deal.getId(), e);
            result.errors.add("Ошибка обновления сделки " + deal.getId() + ": " + e.getMessage());
        }
    }

    private void createNewDealsFromPositions(List<PositionInfo> newPositions, SyncResult result) {
        for (PositionInfo pos : newPositions) {
            Deal restoredDeal = restoreDealFromPosition(pos);
            if (restoredDeal != null) {
                activeDealStore.addDeal(restoredDeal);
                result.created++;
                LoggerUtils.logInfo("🆕 Восстановлена сделка: " + restoredDeal.getId());
            }
        }
    }

    private Deal restoreDealFromPosition(PositionInfo pos) {
        try {
            var strategy = StrategyFactory.getStrategy("ai"); // можно улучшить
            Deal deal = new Deal(pos.getSymbol(), pos.getSide(), EntryType.MARKET, pos.getAvgPrice(),
                    pos.getStopLoss(), new ArrayList<>());

            deal.setId(generateDealIdFromSymbol(pos.getSymbol()));
            deal.setStrategyName("ai");
            deal.updateDealFromBybitPosition(pos);
            restoreOrderIds(deal, pos.getSymbol());

            LoggerUtils.logInfo("🔁 Восстановлена сделка из позиции: " + deal);
            return deal;

        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось восстановить сделку из позиции: " + pos.getSymbol(), e);
            return null;
        }
    }

    private void restoreOrderIds(Deal deal, String symbol) {
        try {
            List<BybitPositionTrackerService.OrderInfo> orders = positionTrackerService.getOrders(symbol);
            for (BybitPositionTrackerService.OrderInfo order : orders) {
                if ("TakeProfit".equals(order.getOrderType()) && order.isReduceOnly()) {
                    OrderManager tpOrder = new OrderManager(order.getOrderId(), OrderManager.OrderType.TP, Double.parseDouble(order.getPrice()));
                    deal.addOrderId(tpOrder);
                }
                if ("StopLoss".equals(order.getOrderType()) && order.isReduceOnly()) {
                    OrderManager slOrder = new OrderManager(order.getOrderId(), OrderManager.OrderType.SL, Double.parseDouble(order.getPrice()));
                    deal.addOrderId(slOrder);
                }
            }
        } catch (IOException e) {
            LoggerUtils.logWarn("⚠️ Не удалось получить ордера для символа " + symbol);
        }
    }

    private String generateDealIdFromSymbol(String symbol) {
        return symbol + "_" + System.currentTimeMillis();
    }

    // --- Вспомогательные классы ---
    public static class SyncResult {
        public int updated = 0;
        public int created = 0;
        public int removed = 0;
        public List<String> errors = new ArrayList<>();

        public boolean isSuccess() {
            return errors.isEmpty();
        }

        public static SyncResult failed(Exception e) {
            SyncResult result = new SyncResult();
            result.errors.add(e.getMessage());
            return result;
        }

        @Override
        public String toString() {
            return "SyncResult{" +
                    "updated=" + updated +
                    ", created=" + created +
                    ", removed=" + removed +
                    ", errors=" + errors +
                    '}';
        }
    }
}