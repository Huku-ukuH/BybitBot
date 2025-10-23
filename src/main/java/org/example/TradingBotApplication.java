
package org.example;
import lombok.Getter;
import lombok.Setter;
import org.example.bot.TradingBot;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.monitor.PriceMonitor;
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


            PriceMonitor priceMonitor = new PriceMonitor(
                    tradingBot.getActiveDealStore(),
                    tradingBot.getMessageSender(),
                    tradingBot.getStopLossManager(),
                    tradingBot.getUpdateManager(),
                    tradingBot.getBybitManager()
            );

            // 🔥 Передаём ссылку на метод, принимающий PriceUpdate
            BybitWebSocketClient webSocketClient = new BybitWebSocketClient(priceMonitor::onPriceUpdate);

            priceMonitor.setWebSocketClient(webSocketClient);
            webSocketClient.connect();
            tradingBot.getActiveDealStore().addOnDealAddedListener(priceMonitor::subscribe);
            tradingBot.getActiveDealStore().addOnDealRemovedListener(priceMonitor::unsubscribe);

            executor.submit(priceMonitor::startMonitoringAllDeals);

            executor.submit(() -> {
                try {
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(tradingBot);
                } catch (Exception e) {
                    LoggerUtils.error("Main - Ошибка запуска Telegram-бота: " + e.getMessage(), e);
                }
            });

            LoggerUtils.info("Main - 🚀 Бот запущен...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LoggerUtils.info("🛑 Завершение работы...");
                webSocketClient.disconnect();
                executor.shutdownNow();
            }));

        } catch (Exception e) {
            LoggerUtils.error("❌ Ошибка запуска программы", e);
            executor.shutdownNow();
        }
    }
}




!!!разбирательство с логами при следующей кодинговой сессии читай тут:

1) логи уже загружены в qwen (документ "отладочный запуск можно найти на рабочем столе"), прочитай ответ нейросети
        и устрани неисправность в коде при повторном обновлении, почему сделка то активна то нет, бесконечные запросы и так далее!!!











