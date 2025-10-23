package org.example.update;

import lombok.Getter;
import org.example.bybit.BybitManager;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.deal.utils.ActiveDealStore;
import org.example.deal.utils.DealCalculator;
import org.example.monitor.dto.PositionInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UpdateManager {

    private final DealUpdater dealUpdater;
    private final UpdateDealCreator updateDealCreator;
    private final BybitManager bybitManager;
    private final OrderRestorer orderRestorer;

    @Getter
    private boolean createDealsProcess = false;
    private List<BybitPositionTrackerService.OrderInfo> pendingOrdersForDealCreation = new ArrayList<>();
    private List<PositionInfo> pendingNewPositions = new ArrayList<>();
    private int currentRestoreIndex = 0;

    public UpdateManager(BybitManager bybitManager, DealCalculator dealCalculator) {
        this.updateDealCreator = new UpdateDealCreator(dealCalculator);
        this.orderRestorer = new OrderRestorer();
        this.dealUpdater = new DealUpdater(orderRestorer);
        this.bybitManager = bybitManager;
    }

    public String updateDeals(ActiveDealStore store, long chatId, String strategyNameInput) throws IOException {
        if (createDealsProcess) {
            return handleDealCreation(strategyNameInput, store, chatId);
        }

        List<PositionInfo> bybitPositionsList = bybitManager.getBybitPositionTrackerService().getPositionList();
        List<BybitPositionTrackerService.OrderInfo> limitOrders = orderRestorer.getUsdtLimitOrders(bybitManager);

        // Сценарий 1: Есть активные позиции → работаем с ними
        if (!bybitPositionsList.isEmpty()) {
            UpdateResult result = dealUpdater.updateExistingDeals(store, bybitPositionsList, bybitManager);
            StringBuilder sb = new StringBuilder("🔄 Результат обновления:\n").append(result.log());

            if (!result.newPositions().isEmpty()) {
                startCreationDealProcess(result.newPositions());
                sb.append("\n🆕 Найдена новая позиция: ")
                        .append(pendingNewPositions.get(0).getSymbol())
                        .append(". Укажите стратегию:");
                return sb.toString();
            }

            // Новых позиций нет → проверяем, есть ли лимитные ордера
            if (!limitOrders.isEmpty()) {
                startCreationDealProcessFromOrders(limitOrders);
                sb.append("\n✅ Все сделки по позициям синхронизированы.\n")
                        .append("🆕 Найдены лимитные ордера. Укажите стратегию для: ")
                        .append(limitOrders.get(0).getSymbol());
                return sb.toString();
            }

            sb.append("\n✅ Все сделки синхронизированы. Лимитных ордеров нет.");
            return sb.toString();
        }

        // Сценарий 2: Нет позиций → работаем с лимитными ордерами
        if (!limitOrders.isEmpty()) {
            startCreationDealProcessFromOrders(limitOrders);
            return "🆕 Найдены лимитные ордера. Укажите стратегию для: " + limitOrders.get(0).getSymbol();
        }

        // Сценарий 3: Ничего нет
        return "✅ Нет активных позиций и лимитных ордеров на Bybit.";
    }
    public PositionInfo updateOneDeal(String symbol) {
        return bybitManager.getBybitPositionTrackerService().getPositionBySymbol(symbol);
    }
    private String handleDealCreation(String strategyName, ActiveDealStore activeDealStore, long chatId) {
        CreationResult result = updateDealCreator.dealCreationTypeSorter(
                strategyName, activeDealStore, chatId, bybitManager, pendingNewPositions, pendingOrdersForDealCreation, currentRestoreIndex, orderRestorer);
        currentRestoreIndex = result.nextIndex();
        createDealsProcess = result.stillCreating();
        return result.message();
    }

    private void startCreationDealProcessFromOrders(List<BybitPositionTrackerService.OrderInfo> orders) {
        this.pendingNewPositions.clear();
        this.pendingOrdersForDealCreation = new ArrayList<>(orders);
        this.currentRestoreIndex = 0;
        this.createDealsProcess = true;
    }

    private void startCreationDealProcess(List<PositionInfo> newPositions) {
        this.pendingOrdersForDealCreation.clear();
        this.pendingNewPositions = new ArrayList<>(newPositions);
        this.currentRestoreIndex = 0;
        this.createDealsProcess = true;
    }
}