
package org.example;
import lombok.Getter;
import lombok.Setter;
import org.example.bot.TradingBot;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.monitor.PriceMonitor;
import org.example.strategy.params.StopLossManager;
import org.example.util.LoggerUtils;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
@Setter
// TradingBotApplication.java
public class TradingBotApplication {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Locale.setDefault(Locale.US);

        try {
            TradingBot tradingBot = new TradingBot();

            StopLossManager stopLossManager = new StopLossManager();
            PriceMonitor priceMonitor = new PriceMonitor(
                    tradingBot.getActiveDealStore(),
                    tradingBot.getMessageSender(), stopLossManager
            );

            // 🔥 Передаём ссылку на метод, принимающий PriceUpdate
            BybitWebSocketClient webSocketClient = new BybitWebSocketClient(priceMonitor::handlePriceUpdate);

            priceMonitor.setWebSocketClient(webSocketClient);
            webSocketClient.connect();
            tradingBot.getActiveDealStore().addOnDealAddedListener(priceMonitor::subscribe);

            executor.submit(priceMonitor::startMonitoringAllDeals);

            executor.submit(() -> {
                try {
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(tradingBot);
                } catch (Exception e) {
                    LoggerUtils.logError("Main - Ошибка запуска Telegram-бота: " + e.getMessage(), e);
                }
            });

            LoggerUtils.logInfo("Main - 🚀 Бот запущен...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LoggerUtils.logInfo("🛑 Завершение работы...");
                webSocketClient.disconnect();
                executor.shutdownNow();
            }));

        } catch (Exception e) {
            LoggerUtils.logError("❌ Ошибка запуска программы", e);
            executor.shutdownNow();
        }
    }
}
