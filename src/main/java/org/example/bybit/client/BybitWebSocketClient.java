// BybitWebSocketClient.java
package org.example.bybit.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Symbol;
import org.example.util.LoggerUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BybitWebSocketClient {
    private static final String WEBSOCKET_URI = "wss://stream.bybit.com/v5/public/linear";
    private final Consumer<String> messageHandler;
    private WebSocketClient client;
    private final Set<String> subscribedSymbols = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public BybitWebSocketClient(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void connect() {
        connectAsync();
        // План переподключения каждые 30 сек, если соединение упало
        scheduler.scheduleAtFixedRate(this::reconnectIfClosed, 30, 30, TimeUnit.SECONDS);
    }

    private void connectAsync() {
        try {
            client = new WebSocketClient(new URI(WEBSOCKET_URI)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LoggerUtils.logInfo("✅ Подключение к WebSocket Bybit установлено");
                    // Восстанавливаем подписки
                    resubscribeAll();
                }

                @Override
                public void onMessage(String message) {
                    if (message.contains("op") && message.contains("success")) {
                        LoggerUtils.logInfo("🟢 Подтверждение подписки: " + message);
                        return;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(message);
                        String symbol = node.at("/data/symbol").asText(null);
                        if (symbol != null) {
                            messageHandler.accept(message); // ← передаём дальше
                        }
                    } catch (JsonProcessingException e) {
                        LoggerUtils.logWarn("Не удалось разобрать JSON: " + message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LoggerUtils.logInfo("❌ Соединение WebSocket закрыто: " + reason + " (code: " + code + ")");
                }

                @Override
                public void onError(Exception ex) {
                    LoggerUtils.logError("🚨 Ошибка WebSocket: " + ex.getMessage(), ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            LoggerUtils.logError("❌ Ошибка запуска WebSocket", e);
        }
    }

    private void reconnectIfClosed() {
        if (client == null || !client.isOpen()) {
            LoggerUtils.logInfo("🔄 Попытка переподключения к WebSocket...");
            connectAsync();
        }
    }

    public void subscribeToTicker(Symbol symbol) {
        String sym = symbol.getSymbol();
        if (subscribedSymbols.add(sym)) { // true, если добавлен
            String topic = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", sym);
            LoggerUtils.logInfo("📡 Подписка на: " + sym);
            sendAsync(topic);
        }
    }

    public void unsubscribeFromTicker(String symbol) {
        if (subscribedSymbols.remove(symbol)) {
            String topic = String.format("{\"op\": \"unsubscribe\", \"args\": [\"tickers.%s\"]}", symbol);
            sendAsync(topic);
            LoggerUtils.logInfo("🚫 Отписка от: " + symbol);
        }
    }

    private void resubscribeAll() {
        if (subscribedSymbols.isEmpty()) return;
        StringBuilder topic = new StringBuilder("{\"op\": \"subscribe\", \"args\": [");
        boolean first = true;
        for (String sym : subscribedSymbols) {
            if (!first) topic.append(",");
            topic.append("\"tickers.").append(sym).append("\"");
            first = false;
        }
        topic.append("]}");
        sendAsync(topic.toString());
        LoggerUtils.logInfo("🔁 Восстановлены подписки: " + subscribedSymbols);
    }

    private void sendAsync(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
        }
    }

    public void disconnect() {
        try {
            if (client != null) {
                client.close();
            }
            scheduler.shutdown();
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при отключении WebSocket", e);
        }
    }

    // Для тестов и отладки
    public Set<String> getSubscribedSymbols() {
        return Set.copyOf(subscribedSymbols);
    }
}