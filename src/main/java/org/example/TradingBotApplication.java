
package org.example;
import lombok.Getter;
import lombok.Setter;
import org.example.ai.AiService;
import org.example.bot.BotCommandHandler;
import org.example.bot.MessageSender;
import org.example.bot.TradingBot;
import org.example.bybit.client.BybitWebSocketClient;
import org.example.bybit.service.BybitOrderService;
import org.example.bybit.service.BybitMonitorService;
import org.example.bybit.auth.BybitAuthConfig;
import org.example.bybit.client.BybitHttpClient;
import org.example.bybit.service.BybitAccountService;
import org.example.bybit.service.BybitMarketService;
import org.example.deal.ActiveDealStore;
import org.example.strategy.params.PartialExitPlanner;
import org.example.monitor.PriceMonitor;
import org.example.monitor.StopLossManager;
import org.example.util.LoggerUtils;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
@Setter
public class TradingBotApplication {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Locale.setDefault(Locale.US); //Установка системы условных знаков (разделяем тысячи точкой, а не запятой как в RU)

        try {

            AiService aiService = new AiService();
            BybitAuthConfig bybitAuthConfig = new BybitAuthConfig();
            BybitHttpClient bybitHttpClient = new BybitHttpClient(bybitAuthConfig);
            BybitAccountService accountService = new BybitAccountService(bybitHttpClient);
            BybitOrderService bybitOrderService = new BybitOrderService(bybitHttpClient);
            BybitMarketService bybitMarketService = new BybitMarketService(bybitHttpClient);


            BybitMonitorService bybitMonitorService = new BybitMonitorService();  //пока не используется

            ActiveDealStore activeDealStore = new ActiveDealStore();
            PartialExitPlanner partialExitPlanner = new PartialExitPlanner();


            BotCommandHandler commandHandler = new BotCommandHandler(
                    aiService, accountService, partialExitPlanner, activeDealStore,
                    bybitOrderService, bybitMonitorService, bybitMarketService);

            TradingBot tradingBot = new TradingBot(commandHandler);
            MessageSender messageSender = new MessageSender(tradingBot);
            commandHandler.setMessageSender(messageSender);

            StopLossManager stopLossManager = new StopLossManager();

            PriceMonitor priceMonitor = new PriceMonitor(
                    activeDealStore, stopLossManager, messageSender
            );
            BybitWebSocketClient webSocketClient = new BybitWebSocketClient(priceMonitor::handleMessage);
            priceMonitor.setWebSocketClient(webSocketClient);
            webSocketClient.connect();

            executor.submit(priceMonitor::startMonitoringAllDeals);

            executor.submit(() -> {
                try {
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(tradingBot);
                } catch (Exception e) {
                    LoggerUtils.logError("Main - Ошибка запуска Telegram-бота: " + e.getMessage(), e);
                }
            });

            LoggerUtils.logInfo("Main - \uD83D\uDE80 Бот запущен...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LoggerUtils.logInfo("\uD83D\uDEA9 Завершение работы программы...");
                webSocketClient.disconnect();
                executor.shutdownNow();
            }));

        } catch (Exception e) {
            LoggerUtils.logInfo("❌ Ошибка запуска программы: " + e.getMessage());
            e.printStackTrace();
            executor.shutdownNow();
        }
    }
}
