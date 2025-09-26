
// PriceMonitor.java
package org.example.monitor;

import lombok.Data;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.bot.MessageSender;
import org.example.deal.ActiveDealStore;
import org.example.deal.Deal;
import org.example.deal.UpdateManager;
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
    private final UpdateManager updateManager;
    private final Map<String, List<Deal>> symbolSubscribers = new ConcurrentHashMap<>();

    public PriceMonitor(ActiveDealStore activeDealStore,
                        MessageSender messageSender,
                        StopLossManager stopLossManager,
                        UpdateManager updateManager)

    {
        this.activeDealStore = activeDealStore;
        this.messageSender = messageSender;
        this.stopLossManager = stopLossManager;
        this.updateManager = updateManager;
    }


    public void subscribe(Deal deal) {
        String symbol = deal.getSymbol().getSymbol();
        symbolSubscribers
                .computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>())
                .add(deal);
        webSocketClient.subscribeToTicker(deal.getSymbol());
    }

    public void unsubscribe(Deal deal) {
        String symbol = deal.getSymbol().getSymbol();
        List<Deal> deals = symbolSubscribers.get(symbol);
        if (deals != null) {
            deals.remove(deal);
            // Если больше никто не слушает этот символ — отписываемся от WebSocket
            if (deals.isEmpty()) {
                webSocketClient.unsubscribeFromTicker(symbol);
                symbolSubscribers.remove(symbol); // чистим мапу
            }
        }
    }

    public void onPriceUpdate(PriceUpdate update) {
        String symbol = update.getSymbol().toString();

        List<Deal> deals = symbolSubscribers.get(symbol);
        if (deals == null || deals.isEmpty()) {
            LoggerUtils.debug("🔍 Нет активных сделок");
            return;
        }

        for (Deal deal : deals) {
            deal.getStrategy().onPriceUpdate(deal, update, updateManager, stopLossManager);
        }

    }

    public void startMonitoringAllDeals() {
        List<Deal> deals = activeDealStore.getAllDeals();
        for (Deal deal : deals) {
            subscribe(deal);
        }
    }
}
