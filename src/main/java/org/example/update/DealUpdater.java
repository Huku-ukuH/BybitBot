package org.example.update;

import org.example.bybit.BybitManager;
import org.example.deal.utils.ActiveDealStore;
import org.example.deal.Deal;
import org.example.monitor.dto.PositionInfo;

import java.util.ArrayList;
import java.util.List;

public class DealUpdater {

    private final OrderRestorer orderRestorer;

    public DealUpdater(OrderRestorer orderRestorer) {
        this.orderRestorer = orderRestorer;
    }

    /**
     * Обновляет все активные сделки на основе текущих позиций.
     */
    public UpdateResult updateExistingDeals(ActiveDealStore store, List<PositionInfo> positions, BybitManager bybitManager) {
        StringBuilder log = new StringBuilder();
        List<String> updatedSymbols = new ArrayList<>();
        List<String> closedSymbols = new ArrayList<>();

        // Копия для поиска новых позиций
        List<PositionInfo> remainingPositions = new ArrayList<>(positions);

        for (Deal deal : store.getAllDeals()) {
            PositionInfo pos = findPosition(positions, deal.getSymbol().toString());

            if (pos == null) {
                // Сделка закрыта
                log.append("🗑️ ").append(deal.getSymbol()).append(" — закрыта, удалена.\n");
                store.removeDeal(deal.getId());
                closedSymbols.add(deal.getSymbol().toString());
            } else {
                // Обновляем данные
                orderRestorer.restoreOrders(deal, bybitManager);
                deal.updateDealFromBybitPosition(pos);
                log.append("✅ ").append(deal.getSymbol()).append(" — обновлена.\n");
                updatedSymbols.add(deal.getSymbol().toString());
                remainingPositions.remove(pos);
            }
        }

        return new UpdateResult(log.toString(), updatedSymbols, closedSymbols, remainingPositions);
    }

    private PositionInfo findPosition(List<PositionInfo> positions, String symbol) {
        return positions.stream()
                .filter(p -> p.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }
}