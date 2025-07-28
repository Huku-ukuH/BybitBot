package org.example.bybit.client;

import org.example.model.Symbol;
import org.example.util.LoggerUtils;
import org.example.util.ValidationUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class BybitWebSocketClient {
    private static final String WEBSOCKET_URI = "wss://stream.bybit.com/v5/public/linear";
    private final Consumer<String> messageHandler;
    private WebSocketClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> subscribedSymbols = new HashSet<>(); // 💡 Храним текущие подписки

    public BybitWebSocketClient(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void connect() {
        try {
            client = new WebSocketClient(new URI(WEBSOCKET_URI)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    LoggerUtils.logInfo("✅ Подключение к WebSocket Bybit установлено");
                }

                @Override
                public void onMessage(String message) {
                    executor.submit(() -> messageHandler.accept(message));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LoggerUtils.logInfo("❌ Соединение WebSocket закрыто: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    LoggerUtils.logError("Ошибка WebSocket: " + ex.getMessage(), ex);
                }
            };

            client.connect();
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка подключения к WebSocket", e);
            throw new RuntimeException(e);
        }
    }

    public void subscribeToTicker(Symbol symbol) {
        ValidationUtils.checkNotNull(symbol, "Symbol не может быть null");
        String sym = symbol.getSymbol();

        // ❗ Подписываемся только если не подписаны
        if (!subscribedSymbols.contains(sym)) {
            String topic = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", sym);
            LoggerUtils.logInfo("📡 Подписка на тикер: " + topic);
            if (client != null && client.isOpen()) {
                client.send(topic);
                subscribedSymbols.add(sym);
            }
        }
    }

    // 📌 Метод для массовой подписки
    public void updateSubscriptions(List<String> activeSymbols) {
        for (String symbol : activeSymbols) {
            subscribeToTicker(new Symbol(symbol));
        }
    }

    // ❌ Метод для отписки от тикера (если понадобится)
    public void unsubscribeFromTicker(String symbol) {
        if (subscribedSymbols.contains(symbol)) {
            String topic = String.format("{\"op\": \"unsubscribe\", \"args\": [\"tickers.%s\"]}", symbol);
            LoggerUtils.logInfo("🚫 Отписка от тикера: " + topic);
            if (client != null && client.isOpen()) {
                client.send(topic);
                subscribedSymbols.remove(symbol);
            }
        }
    }

    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
        }
        executor.shutdown();
    }
}
