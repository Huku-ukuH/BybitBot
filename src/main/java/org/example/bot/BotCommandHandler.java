package org.example.bot;

import lombok.Getter;
import lombok.Setter;
import org.example.ai.AiService;
import org.example.bybit.BybitManager;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.deal.*;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.params.ExitPlan;
import org.example.strategy.params.ExitPlanManager;
import org.example.strategy.strategies.StrategyFactory;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;

import java.util.Arrays;
import java.util.List;


@Getter
@Setter
public class BotCommandHandler {
    private BybitManager bybitManager;
    private final ActiveDealStore activeDealStore;
    private String strategyName = "ai";
    private boolean waitingSignal = false;
    private MessageSender messageSender;
    private final AiService aiService;
    private boolean justChat = false;
    private DealRequest dealRequest;
    private String currentDealId;
    private Deal deal;
    // -----------------

    public BotCommandHandler(BybitManager bybitManager, AiService aiService, ActiveDealStore activeDealStore, MessageSender messageSender) {
        this.bybitManager = bybitManager;
        this.activeDealStore = activeDealStore;
        this.messageSender = messageSender;
        this.aiService = aiService;

    }
    public void handleCommand(long chatId, String command, String messageText) {
        switch (command.toLowerCase()) {
            case "/start", "/help" -> sendHelpMessage(chatId);
            case "/getsgnl" -> handleGetSignal(chatId, messageText);
            case "/check" -> handleCheck(chatId);
            case "/amount" -> handleAmount(chatId);
            case "/go" -> handleGo(chatId);
            case "/list" -> handleList(chatId);
            case "/write" -> handleWrite(chatId, messageText);
            case "/orders" -> handleOrders(chatId);
            case "/calculate" -> handleCalculate(chatId);
            case "/lossupdate" -> updateLossPrecent(chatId);
            case "/exit" -> handleExit(chatId);
            case "/update" -> handleUpdate(chatId);
            case "/setstrat" -> handleSetStrategy(chatId, messageText);
            default -> messageSender.send(chatId, EmojiUtils.INFO + " Неизвестная команда: " + command);
        }
    }
    private void sendHelpMessage(long chatId) {
        String helpText = EmojiUtils.PAPER + """
                 Доступные команды:
                /getSgnl - получить сигнал от пользователя
                /check - проверить сделку через ИИ
                /amount - рассчитать размер позиции
                /go - подтвердить и отправить сделку в Bybit
                /tpadd - предложить частичный выход по TP
                /list - список активных сделок
                /write - добавить заметку к сделке
                /orders - информация по ордерам
                /calculate - расчёт средней цены входа
                /exit - отмена текущего действия
                /update - обновить сделки из Bybit
                /setstrat <strategy_name> - установить стратегию по умолчанию для новых сделок (например, /setstrat fixed_risk)
                """; // <-- Обновлённый текст помощи
        messageSender.send(chatId, helpText);
    }
    private void cycleBreak(long chatId) {
        if (activeDealStore.containsDeal(deal.getId())) {
           messageSender.send(chatId, bybitManager.getBybitOrderService().closeDeal(deal));

        }
        if (currentDealId != null) {
            activeDealStore.removeDeal(currentDealId);
            LoggerUtils.logInfo("cycleBreak() " + deal + "удалена из activeDealStore");
        }
        currentDealId = null;
        deal = null;
    }

    private void handleGetSignal(long chatId, String messageText) {
        if (!waitingSignal) {
            waitingSignal = true;
            messageSender.send(chatId, "Жду сигнал");
            return;
        }
        messageText = messageText.replace("/getsgnl", "").trim();
        if (messageText.isEmpty()) {
            messageSender.sendWarn(chatId, "Сигнал пустой. Отмена.", "handleGetSignal()");
            waitingSignal = false;
            return;
        }
        if (messageText.startsWith("/")) {
            messageSender.sendWarn(chatId, "Нельзя отправлять команду как сигнал. Отмена.", "handleGetSignal()");
            LoggerUtils.logWarn("Попытка использовать команду как сигнал: " + messageText);
            waitingSignal = false;
            return;
        }
        try {
            deal = StrategyFactory.getStrategy(strategyName).createDeal(aiService, messageText, chatId, strategyName);
            currentDealId = deal.getId();
            messageSender.send(chatId, deal.toString());
            waitingSignal = false;
        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка обработки сигнала", e, "handleGetSignal()");
            cycleBreak(chatId);
            waitingSignal = false;
        }
    }
    private void handleCheck(long chatId) {
        if (deal == null) {
            messageSender.sendWarn(chatId, "Проверять нечего, Deal is null", "handleCheck()");
            cycleBreak(chatId);
            return;
        }
        try {
            DealValidationResult result = deal.getStrategy().validateDeal(deal, bybitManager.getBybitMarketService());

            if (!result.getErrors().isEmpty()) {
                messageSender.send(chatId, result.formatErrors().toString());
                cycleBreak(chatId);
                return;
            }

            if (!result.getWarnings().isEmpty()) {
                messageSender.send(chatId, result.formatWarnings().toString());
            } else {
                messageSender.send(chatId, EmojiUtils.OKAY + " Проверка пройдена: всё в порядке");
            }

        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка проверки сделки", e, "handleCheck()");
        }
    }

    private void handleAmount(long chatId) {
        if (deal == null) {
            messageSender.sendWarn(chatId, "Проверять нечего, Deal is null", "handleAmount()");
            return;
        }
        try {
            messageSender.send(chatId,  EmojiUtils.OKAY + "\n" + deal.getStrategy().calculateDeal(deal, new DealCalculator(bybitManager.getBybitAccountService(), bybitManager.getBybitMarketService())));

        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка расчёта позиции", e, "handleAmount()");
        }
    }

    private void handleGo(long chatId) {
        if (deal == null) {
            messageSender.sendWarn(chatId, "Сделки нет! ", "handleGo()");
            return;
        }

        StringBuilder result = new StringBuilder();
        try {
            // 1. Устанавливаем плечо (можно делать до входа)
            if (bybitManager.getBybitOrderService().setLeverage(deal)) {
                result.append(EmojiUtils.OKAY + " Leverage\n");
            }

            // 2. Выставляем ордер на вход (маркет или лимит)
            BybitOrderRequest request = BybitOrderRequest.forEntry(deal);
            BybitOrderResponse orderResponse = bybitManager.getBybitOrderService().placeOrder(request);
            if (orderResponse.isSuccess()) {
                result.append(EmojiUtils.OKAY + " Order\n");
                deal.setId(orderResponse.getOrderResult().getOrderId());
                currentDealId = deal.getId();
                // Сохраняем сделку ДО активации
                activeDealStore.addDeal(deal);

                // Если это МАРКЕТ-ордер — позиция уже открыта → активируем сразу
                if (deal.getEntryType() == EntryType.MARKET) {
                    goIfDealOpen(chatId, deal);
                }
                // Если это ЛИМИТ — ждём исполнения, активация будет позже
                else {
                    messageSender.send(chatId, " Лимитный ордер выставлен. Ожидаем вход...");
                }
            } else {
                messageSender.sendWarn(chatId, "❌ Ошибка при создании ордера: " + orderResponse.getRetMsg(), "handleGo()");
            }
        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка при создании сделки: " + e.getMessage(), e, "handleGo()");
        }
    }

    public void goIfDealOpen(long chatId, Deal deal) {

        String result = "null";
        try {
            deal.setActive(true);
            ExitPlan plan = deal.getStrategy().planExit(deal);
            result = new ExitPlanManager(new DealCalculator(bybitManager.getBybitAccountService(), bybitManager.getBybitMarketService()), bybitManager.getBybitOrderService()).executeExitPlan(deal, plan);
        } catch (Exception e) {
            LoggerUtils.logError("Ошибка при планировании выхода (TP)", e);
            messageSender.sendError(chatId, "Ошибка при установке тейк-профитов", e, "goIfDealOpen()");
        }

        // 🔥 Попытка установить стоп-лосс
        try {
            BybitOrderResponse stopLossResponse = bybitManager.getBybitOrderService().setStopLoss(deal);
            if (!stopLossResponse.isSuccess()) {
                throw new IllegalStateException("Bybit вернул успех, но не установил SL");
            }
            LoggerUtils.logInfo("✅ Стоп-лосс установлен для " + deal.getSymbol() + ": " + deal.getStopLoss());
        } catch (Exception e) {
            LoggerUtils.logError("❌ Не удалось установить стоп-лосс для " + deal.getSymbol(), e);
            messageSender.sendWarn(chatId, EmojiUtils.CROSS + " КРИТИЧКО: Не удалось установить стоп-лосс! Сделка отменена.", "goIfDealOpen()->BybitOrderService().setStopLoss(deal)");
            cycleBreak(chatId);
            return;
        }

        activeDealStore.addDeal(deal);
        messageSender.send(chatId, EmojiUtils.OKAY + "Сделка открыта!\n" + deal.bigDealToString() + "\n" + result);
    }




    private void handleList(long chatId) {
        List<Deal> deals = activeDealStore.getAllDeals();
        if (deals.isEmpty()) {
            messageSender.sendWarn(chatId, "Нет активных сделок.", "handleList()");
            return;
        }
        StringBuilder sb = new StringBuilder("Активные сделки:\n");
        for (Deal deal : deals) {
            sb.append("- ").append(deal.getSymbol()).append(deal.getDirection() == Direction.LONG ? "L" : "S").append("\n");
        }
        messageSender.send(chatId, sb.toString());
    }

    private void handleWrite(long chatId, String messageText) {
        if (!justChat) {
            messageSender.send(chatId, "Функция добавления заметок пока не реализована. Переключено в режим общения с ИИ");
            justChat = true;
            return;
        }
        messageSender.send(chatId, "Функция добавления заметок пока не реализована. Переключено в режим работы со сделками");
        justChat = false;
    }

    private void handleOrders(long chatId) {
        messageSender.send(chatId, "Функция просмотра ордеров пока не реализована.");
    }

    private void handleCalculate(long chatId) {
        messageSender.send(chatId, "Функция расчёта средней цены входа пока не реализована.");
    }

    private void handleExit(long chatId) {
        messageSender.send(chatId, "Cделка обнулена.");
        cycleBreak(chatId);
    }

    //метод обновления, а точнее метод восстановления сделок после перезагрузки бота,
    // но пока это просто метод для обновления информации о сделках
    private void handleUpdate(long chatId) {
        messageSender.send(chatId, "Обновление сделок из Bybit...");
        // TODO: реализовать обновление сделок из Bybit, а пока будет просто обновление информации о позициях


        messageSender.send(chatId, "🔄 Обновление сделок из Bybit...");
        for (Deal deal : activeDealStore.getAllDeals()) {
            try {
                PositionInfo pos = bybitManager.getBybitPositionTrackerService().getPosition(deal.getSymbol().getSymbol());
                if (pos == null) {
                    // Позиция закрыта вручную
                    messageSender.send(chatId, "🗑️ Позиция " + deal.getSymbol() + " больше не активна (закрыта на бирже ).");
                    activeDealStore.removeDeal(deal.getId());
                } else {
                    // Обновляем состояние
                    deal.updateDealFromBybitPosition(pos); // реализуйте этот метод
                }
            } catch (Exception e) {
                LoggerUtils.logError("Ошибка обновления позиции для " + deal.getSymbol(), e);
            }
        }
    }

    // --- Вспомогательные методы --- //
    private Deal getActiveDeal(long chatId) {
        List<Deal> deals = activeDealStore.getAllDeals();
        if (deals.isEmpty()) {
            messageSender.send(chatId, "Нет активных сделок для обработки.");
            return null;
        }
        return deals.get(0); // Для простоты берем первую
    }

    // --- НОВЫЙ МЕТОД ---
    /**
     * Обработчик команды /setstrat
     * Устанавливает стратегию по умолчанию для будущих сделок.
     * Пример использования: /setstrat fixed_risk
     */
    private void handleSetStrategy(long chatId, String messageText) {
        // Извлекаем название стратегии из текста сообщения
        String[] parts = messageText.trim().split("\\s+", 2); // Разделяем по пробелам, максимум на 2 части
        if (parts.length < 2) {
            messageSender.sendWarn(chatId, "Укажите название стратегии. Доступные: " + Arrays.toString(StrategyFactory.getAvailableStrategies().toArray()), "handleSetStrategy()");
            return;
        }

        String strategyNameInput = parts[1].trim();

        // Проверяем, существует ли такая стратегия
        if (!StrategyFactory.isStrategyAvailable(strategyNameInput)) {
            messageSender.sendWarn(chatId, "Стратегия '" + strategyNameInput + "' не найдена. Доступные: " + Arrays.toString(StrategyFactory.getAvailableStrategies().toArray()), "handleSetStrategy()");
            return;
        }

        // Сохраняем стратегию по умолчанию
        this.strategyName = strategyNameInput.toLowerCase(); // Приводим к нижнему регистру для единообразия
        messageSender.send(chatId, EmojiUtils.OKAY + " Стратегия по умолчанию для новых сделок установлена на: " + this.strategyName);
        LoggerUtils.logInfo("Стратегия по умолчанию изменена пользователем на " + this.strategyName);
    }
    // ------------------
    private  void updateLossPrecent(long chatId) {
        double updateLoss = StrategyFactory.getStrategy(strategyName).RiskUpdate(bybitManager.getBybitAccountService());
        String message = "Предел риска обновлен на " +  updateLoss + "$ на позицию";
        messageSender.send(chatId, message);
        LoggerUtils.logInfo(message);
    }
}
