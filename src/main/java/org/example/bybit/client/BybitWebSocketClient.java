// BybitWebSocketClient.java
package org.example.bybit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.example.model.Symbol;
import org.example.monitor.dto.PriceUpdate;
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
    private static final String WEBSOCKET_URI = Dotenv.load().get("WEBSOCKET_URI");

    private final Consumer<PriceUpdate> messageHandler;

    private WebSocketClient client;
    private final Set<String> subscribedSymbols = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper(); // можно использовать ваш JsonUtils.createObjectMapper()
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public BybitWebSocketClient(Consumer<PriceUpdate> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void connect() {
        connectAsync(); // ← запускает подключение в фоне
        scheduler.scheduleAtFixedRate(this::reconnectIfClosed, 30, 30, TimeUnit.SECONDS);
    }
    private void connectAsync() {
        try {
            client = new WebSocketClient(new URI(WEBSOCKET_URI)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LoggerUtils.info("✅ Подключение к WebSocket Bybit установлено");
                    resubscribeAll();
                }

                @Override
                public void onMessage(String message) {
                    if (message.contains("op") && message.contains("success")) {
                        LoggerUtils.info("🟢 Подтверждение подписки: " + message);
                        return;
                    }

                    try {
                        JsonNode root = objectMapper.readTree(message);
                        JsonNode dataNode = root.path("data");

                        if (dataNode.isMissingNode()) return;

                        // Поддержка одиночного объекта и массива
                        if (dataNode.isArray()) {
                            for (JsonNode node : dataNode) {
                                processTickerNode(node);
                            }
                        } else {
                            processTickerNode(dataNode);
                        }
                    } catch (Exception e) {
                        LoggerUtils.error("Ошибка парсинга WebSocket-сообщения: " + message, e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LoggerUtils.info("❌ Соединение WebSocket закрыто: " + reason + " (code: " + code + ")");
                }

                @Override
                public void onError(Exception ex) {
                    LoggerUtils.error("🚨 Ошибка WebSocket: " + ex.getMessage(), ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            LoggerUtils.error("❌ Ошибка запуска WebSocket", e);
        }
    }

    // Отдельный метод для обработки одного тикера
    private void processTickerNode(JsonNode node) {
        try {
            Symbol symbol = new Symbol(node.path("symbol").asText(null));
            String lastPriceStr = node.path("lastPrice").asText(null);

            if (symbol == null || lastPriceStr == null) return;

            double lastPrice = Double.parseDouble(lastPriceStr.trim());

            // Создаём DTO и отправляем дальше
            PriceUpdate update = new PriceUpdate(symbol, lastPrice);
            messageHandler.accept(update);

        } catch (NumberFormatException e) {
            LoggerUtils.error("Некорректная цена в тикере: " + node, e);
        } catch (Exception e) {
            LoggerUtils.error("Ошибка при обработке тикера: " + e.getMessage(), e);
        }
    }

    private void reconnectIfClosed() {
        if (client == null || !client.isOpen()) {
            LoggerUtils.info("🔄 Попытка переподключения к WebSocket...");
            connectAsync();
        }
    }

    public void subscribeToTicker(Symbol symbol) {
        String sym = symbol.getSymbol();
        if (subscribedSymbols.add(sym)) { // true, если добавлен
            String topic = String.format("{\"op\": \"subscribe\", \"args\": [\"tickers.%s\"]}", sym);
            LoggerUtils.info("📡 Подписка на: " + sym);
            sendAsync(topic);
        }
    }

    public void unsubscribeFromTicker(String symbol) {
        if (subscribedSymbols.remove(symbol)) {
            String topic = String.format("{\"op\": \"unsubscribe\", \"args\": [\"tickers.%s\"]}", symbol);
            sendAsync(topic);
            LoggerUtils.info("🚫 Отписка от: " + symbol);
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
        LoggerUtils.info("🔁 Восстановлены подписки: " + subscribedSymbols);
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
            LoggerUtils.error("Ошибка при отключении WebSocket", e);
        }
    }

    // Для тестов и отладки
    public Set<String> getSubscribedSymbols() {
        return Set.copyOf(subscribedSymbols);
    }
}