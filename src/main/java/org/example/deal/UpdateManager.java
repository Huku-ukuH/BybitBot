package org.example.deal;

import lombok.Getter;
import org.example.bybit.BybitManager;
import org.example.bybit.service.BybitPositionTrackerService;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.strategies.strategies.StrategyFactory;
import org.example.util.EmojiUtils;
import org.example.util.JsonUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.util.*;


/**
 * Сервис для синхронизации состояния сделок в приложении с данными на бирже Bybit.
 * Восстанавливает сделки после перезапуска, привязывает orderId ордеров (TP/SL),
 * обновляет текущее состояние позиций.
 * <p>
 * Не взаимодействует с пользователем напрямую — только логирует и возвращает результат.
 */
public class UpdateManager {
    @Getter
    private boolean createDealsProcess = false;

    // Для временного хранения новых позиций во время восстановления
    private List<PositionInfo> pendingNewPositions = new ArrayList<>();
    private int currentRestoreIndex = 0;

    /**
     * Основной метод обновления и восстановления сделок.
     */
    public String updateDeals(BybitManager bybitManager, ActiveDealStore activeDealStore, long chatId, String strategyNameInput) throws IOException {
        // ШАГ 1: Если идёт процесс восстановления — передаём управление
        if (createDealsProcess) {
            return createNextDeal(strategyNameInput, activeDealStore, chatId, bybitManager);
        }

        StringBuilder result = new StringBuilder("🔄 Результат обновления:\n");
        List<PositionInfo> exchangePositions = bybitManager.getBybitPositionTrackerService().getPositionList();

        if (exchangePositions.isEmpty()) {
            result.append("✅ Нет открытых позиций на Bybit.");
            return result.toString();
        }

        // ШАГ 2: Обновляем существующие сделки
        List<PositionInfo> newPositions = new ArrayList<>(exchangePositions); // копия для удаления

        for (Deal deal : activeDealStore.getAllDeals()) {
            PositionInfo posOnExchange = findPosition(exchangePositions, deal.getSymbol().getSymbol());

            if (posOnExchange == null) {
                // Сделка закрыта
                result.append("🗑️ ").append(deal.getSymbol()).append(" — закрыта, удалена.\n");
                activeDealStore.removeDeal(deal.getId());
            } else {
                deal.updateDealFromBybitPosition(posOnExchange);
                result.append(restoreOrderIds(deal, posOnExchange.getSymbol().toString(), bybitManager));
                result.append("✅ ").append(deal.getSymbol()).append(" — обновлена.\n");
                newPositions.remove(posOnExchange); // убираем из списка "новых"
            }
        }

        // ШАГ 3: Есть ли новые позиции?
        if (!newPositions.isEmpty()) {
            this.pendingNewPositions = new ArrayList<>(newPositions);
            this.currentRestoreIndex = 0;
            this.createDealsProcess = true;

            PositionInfo first = pendingNewPositions.get(0);
            result.append("\n🆕 Найдена новая позиция: ").append(first.getSymbol())
                    .append(". Укажите стратегию:");
        } else {
            result.append("\n✅ Все сделки синхронизированы.");
        }

        return result.toString();
    }

    /**
     * Создаёт одну сделку после ввода стратегии пользователем.
     */
    private String createNextDeal(String strategyName, ActiveDealStore activeDealStore, long chatId, BybitManager bybitManager) {
        if (currentRestoreIndex >= pendingNewPositions.size()) {
            createDealsProcess = false;
            return "❌ Нет сделок для восстановления.";
        }

        PositionInfo pos = pendingNewPositions.get(currentRestoreIndex);
        currentRestoreIndex++;

        // Проверяем стратегию
        if (!StrategyFactory.isStrategyAvailable(strategyName)) {
            currentRestoreIndex--; // вернём индекс назад
            return "⚠️ Стратегия '" + strategyName + "' не найдена. Доступные: " +
                    String.join(", ", StrategyFactory.getAvailableStrategies());
        }

        try {
            Deal restoredDeal = StrategyFactory.getStrategy(strategyName).createDeal(pos, chatId, strategyName);
            restoredDeal.setId(pos.getSymbol() + "_" + strategyName + "_" + System.currentTimeMillis());

            restoreOrderIds(restoredDeal, pos.getSymbol().toString(), bybitManager);
            activeDealStore.addDeal(restoredDeal);

            StringBuilder result = new StringBuilder();
            result.append("✅ Сделка для ").append(pos.getSymbol()).append(" восстановлена со стратегией '").append(strategyName).append("'.\n");

            if (currentRestoreIndex < pendingNewPositions.size()) {
                PositionInfo next = pendingNewPositions.get(currentRestoreIndex);
                result.append("\n🆕 Следующая: ").append(next.getSymbol()).append(". Укажите стратегию:");
            } else {
                createDealsProcess = false;
                result.append("\n✅ Все сделки восстановлены.");
            }

            LoggerUtils.info("✅ Восстановлена сделка: id=" + restoredDeal.getId() + ", TP orderId=" + restoredDeal.getTpOrderId() + ", SL orderId=" + restoredDeal.getSlOrderId());

            return result.toString();

        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при создании сделки для " + pos.getSymbol(), e);
            return "❌ Ошибка: " + e.getMessage();
        }
    }

    // --- Вспомогательные ---
    private PositionInfo findPosition(List<PositionInfo> positions, String symbol) {
        return positions.stream()
                .filter(p -> p.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }

    private String restoreOrderIds(Deal deal, String symbol, BybitManager bybitManager) {

        StringBuilder result = new StringBuilder();
        try {
            List<BybitPositionTrackerService.OrderInfo> orders = bybitManager.getBybitPositionTrackerService().getOrders(symbol);
            if (orders == null || orders.isEmpty()) {
                return "📭 Нет активных ордеров для символа " + symbol;
            }

            LoggerUtils.logInfo("📥 ОРДЕРА " + symbol + ": " + JsonUtils.toJson(orders));

            for (BybitPositionTrackerService.OrderInfo order : orders) {

                // Пропускаем, если не reduceOnly
                if (!Boolean.TRUE.equals(order.getReduceOnly())) {
                    continue;
                }

                // Парсим triggerPrice
                double triggerPrice;
                try {
                    triggerPrice = Double.parseDouble(order.getTriggerPrice());
                } catch (NumberFormatException | NullPointerException e) {
                    result.append("⚠️ Не удалось распарсить triggerPrice у ордера ").append(order.getOrderId()).append("\n");
                    continue;
                }

                // Проверяем stopOrderType
                if ("StopLoss".equals(order.getStopOrderType())) {
                    deal.addOrderId(new OrderManager(
                            order.getOrderId(),
                            OrderManager.OrderType.SL,
                            triggerPrice
                    ));
                    result.append("🔗 Привязан SL: ").append(order.getOrderId()).append(" -> ").append(triggerPrice).append("\n");
                }

                if ("TakeProfit".equals(order.getStopOrderType()) ||
                        "PartialTakeProfit".equals(order.getStopOrderType())) {
                    deal.addOrderId(new OrderManager(
                            order.getOrderId(),
                            OrderManager.OrderType.TP,
                            triggerPrice
                    ));
                    result.append("🔗 Привязан TP: ").append(order.getOrderId()).append(" -> ").append(triggerPrice).append("\n");
                }
            }

        } catch (IOException e) {
            LoggerUtils.logError("⚠️ Не удалось загрузить ордера с Bybit для символа " + symbol, e);
        } catch (NumberFormatException e) {
            LoggerUtils.logError("❌ Ошибка парсинга цены при привязке TP/SL", e);
        }
        return result.toString();
    }

}
