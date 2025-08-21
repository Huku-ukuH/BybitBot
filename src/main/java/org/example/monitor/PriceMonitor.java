
// PriceMonitor.java
package org.example.monitor;

import lombok.Data;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.bot.MessageSender;
import org.example.deal.ActiveDealStore;
import org.example.deal.Deal;
import org.example.strategy.params.StopLossManager;
import org.example.util.LoggerUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.ArrayList;

@Data
public class PriceMonitor {
    private BybitWebSocketClient webSocketClient;
    private final ActiveDealStore activeDealStore;
    private final StopLossManager stopLossManager;
    private final MessageSender messageSender;
    private final Map<String, List<Deal>> symbolSubscribers = new ConcurrentHashMap<>();

    public PriceMonitor(ActiveDealStore activeDealStore,
                        StopLossManager stopLossManager,
                        MessageSender messageSender) {
        this.activeDealStore = activeDealStore;
        this.stopLossManager = stopLossManager;
        this.messageSender = messageSender;
    }


    public void subscribe(Deal deal) {
        String symbol = deal.getSymbol().getSymbol();
        symbolSubscribers
                .computeIfAbsent(symbol, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(deal);
        webSocketClient.subscribeToTicker(deal.getSymbol());
    }

    public void handleMessage(String message) {
        Pattern symbolPattern = Pattern.compile("\\\"symbol\\\":\\\"(.*?)\\\"");
        Pattern pricePattern = Pattern.compile("\\\"lastPrice\\\":\\\"(.*?)\\\"");

        Matcher symbolMatcher = symbolPattern.matcher(message);
        Matcher priceMatcher = pricePattern.matcher(message);

        if (symbolMatcher.find() && priceMatcher.find()) {
            String symbol = symbolMatcher.group(1);
            double price = Double.parseDouble(priceMatcher.group(1));
            onPriceUpdate(symbol, price);
        }
    }

    public void onPriceUpdate(String symbol, double currentPrice) {
        List<Deal> deals = symbolSubscribers.get(symbol);
        if (deals == null || deals.isEmpty()) return;

        synchronized (deals) {
            for (Deal deal : deals) {
                if (!deal.isActive()) {
                    continue;
                }
                boolean slUpdated = stopLossManager.moveStopLoss(deal, currentPrice);
                if (slUpdated) {
                    String message = String.format("✅ Стоп-лосс обновлён для %s: %.2f", symbol, deal.getStopLoss());
                    messageSender.send(deal.getChatId(), message);
                    LoggerUtils.logInfo(message);
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
