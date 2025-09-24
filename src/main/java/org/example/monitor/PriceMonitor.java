
// PriceMonitor.java
package org.example.monitor;

import lombok.Data;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.bot.MessageSender;
import org.example.deal.ActiveDealStore;
import org.example.deal.Deal;
import org.example.monitor.dto.PriceUpdate;
import org.example.strategy.params.StopLossManager;
import org.example.util.LoggerUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class PriceMonitor {
    private BybitWebSocketClient webSocketClient;
    private final ActiveDealStore activeDealStore;
    private final StopLossManager stopLossManager;
    private final MessageSender messageSender;
    private final Map<String, List<Deal>> symbolSubscribers = new ConcurrentHashMap<>();

    public PriceMonitor(ActiveDealStore activeDealStore,
                        MessageSender messageSender, StopLossManager stopLossManager) {
        this.activeDealStore = activeDealStore;
        this.messageSender = messageSender;
        this.stopLossManager = stopLossManager;
    }


    public void subscribe(Deal deal) {
        String symbol = deal.getSymbol().getSymbol();
        symbolSubscribers
                .computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>())
                .add(deal);
        webSocketClient.subscribeToTicker(deal.getSymbol());
    }

    public void handlePriceUpdate(PriceUpdate update) {
        LoggerUtils.info("📈 Цена обновлена: " + update.getSymbol() + " = " + update.getPrice());
        onPriceUpdate(update.getSymbol(), update.getPrice());
    }




    // Работать дальше с этим методом, он должен у каждой сделки вызывать стратегию, а у стратегии метод onPriceUpdate.
    // Стоп лосс сам по себе обновляться не должен, иначе это получается внеплановый трейлинг, все должно быть согласовано со стратегией,
    // пока что дефолтный вариант- ставить стоп на(под твх в зону безубытка) при исполнении первого тейка
    public void onPriceUpdate(String symbol, double currentPrice) {
        List<Deal> deals = symbolSubscribers.get(symbol);
        if (deals == null || deals.isEmpty()) {
            LoggerUtils.debug("🔍 Нет активных сделок для: " + symbol);
            return;
        }

        LoggerUtils.info("🔄 Проверка " + deals.size() + " сделок по " + symbol + " при цене " + currentPrice);

        synchronized (deals) {
            for (Deal deal : deals) {
                if (!deal.isActive()) {
                    LoggerUtils.debug("⏭️ Сделка неактивна: " + deal.getSymbol());
                    continue;
                }

                boolean slUpdated = stopLossManager.moveStopLoss(deal, currentPrice);
                if (slUpdated) {
                    String message = String.format("✅ Стоп-лосс обновлён для %s: %.2f", symbol, deal.getStopLoss());
                    messageSender.send(deal.getChatId(), message);
                    LoggerUtils.info(message);
                }
            }
        }
    }

    public void startMonitoringAllDeals() {
        List<Deal> deals = activeDealStore.getAllDeals();
        for (Deal deal : deals) {
            subscribe(deal);
        }
    }
}
