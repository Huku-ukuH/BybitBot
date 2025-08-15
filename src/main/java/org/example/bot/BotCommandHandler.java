package org.example.bot;

import lombok.Getter;
import lombok.Setter;
import org.example.ai.AiService;
import org.example.bybit.dto.BybitOrderRequest;
import org.example.bybit.dto.BybitOrderResponse;
import org.example.bybit.service.*;
import org.example.deal.*;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.deal.dto.PartialExitPlan;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.params.PartialExitPlanner;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;
import org.example.strategy.strategies.StrategyFactory;
import java.util.Arrays;
import java.util.List;


@Getter
@Setter
public class BotCommandHandler {
    BybitPositionTrackerService bybitPositionTrackerService;
    private final BybitAccountService bybitAccountService;
    private final BybitMonitorService bybitMonitorService;
    private final PartialExitPlanner partialExitPlanner;
    private final BybitMarketService bybitMarketService;
    private final BybitOrderService bybitOrderService;
    private final ActiveDealStore activeDealStore;
    private final DealCalculator dealCalculator;
    private String defaultStrategyName = "ai";
    private boolean waitingSignal = false;
    private MessageSender messageSender;
    private final AiService aiService;
    private boolean justChat = false;
    private DealRequest dealRequest;
    private String currentDealId;
    private Deal deal;
    // -----------------

    public BotCommandHandler(
            AiService aiService,
            BybitAccountService bybitAccountService,
            ActiveDealStore activeDealStore,
            BybitOrderService bybitOrderService,
            BybitMonitorService bybitMonitorService,
            BybitMarketService bybitMarketService, BybitPositionTrackerService bybitPositionTrackerService) {
        dealCalculator = new DealCalculator(bybitAccountService, bybitMarketService);
        partialExitPlanner = new PartialExitPlanner();
        this.bybitPositionTrackerService = bybitPositionTrackerService;
        this.aiService = aiService;
        this.bybitAccountService = bybitAccountService;
        this.activeDealStore = activeDealStore;
        this.bybitOrderService = bybitOrderService;
        this.bybitMarketService = bybitMarketService;
        this.bybitMonitorService = bybitMonitorService;
    }
    public void handleCommand(long chatId, String command, String messageText) {
        switch (command.toLowerCase()) {
            case "/start", "/help" -> sendHelpMessage(chatId);
            case "/getsgnl" -> handleGetSignal(chatId, messageText);
            case "/check" -> handleCheck(chatId);
            case "/amount" -> handleAmount(chatId);
            case "/go" -> handleGo(chatId);
            case "/tpadd" -> handleTpAdd(chatId);
            case "/list" -> handleList(chatId);
            case "/write" -> handleWrite(chatId, messageText);
            case "/orders" -> handleOrders(chatId);
            case "/calculate" -> handleCalculate(chatId);
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
    private void cycleBreak() {
        if (currentDealId != null) {
            activeDealStore.removeDeal(currentDealId);
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
        messageText = messageText.replace("/getsgnl", "");
        try {
            deal = new Deal(aiService.parseSignal(messageText));
            deal.setChatId(chatId);
            currentDealId = deal.getId();
            deal.setStrategyName(this.defaultStrategyName);
            messageSender.send(chatId, deal.toString());
            waitingSignal = false;
        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка обработки сигнала", e, "handleGetSignal()\nОтвет нейронки: " + aiService.parseSignal(messageText));
            cycleBreak();
            waitingSignal = false;
        }
    }
    private void handleCheck(long chatId) {
        if (deal == null) {
            messageSender.sendWarn(chatId, "Проверять нечего, Deal is null", "handleCheck()");
            cycleBreak();
            return;
        }
        try {
            DealValidationResult result = new DealValidator().validate(deal, bybitMarketService);
            if (!result.getErrors().isEmpty()) {
                messageSender.send(chatId, result.formatErrors().toString());
                cycleBreak();
                return;
            }
            messageSender.send(chatId, result.formatWarnings().toString());
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
            messageSender.send(chatId, dealCalculator.calculate(deal) + "\n" + EmojiUtils.OKAY + "значения добавлены в Deal");
            // удаление текущей цены из переменной
            if (deal.getEntryType() == EntryType.MARKET) {
                deal.setEntryPrice(null);
            }
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
            if (bybitOrderService.setLeverage(deal)) {
                result.append(EmojiUtils.OKAY + " Leverage\n");
            }

            // 2. Выставляем ордер на вход (маркет или лимит)
            BybitOrderRequest request = new BybitOrderRequest(deal);
            BybitOrderResponse orderResponse = bybitOrderService.placeOrder(request, deal);

            if (orderResponse.isSuccess()) {
                result.append(EmojiUtils.OKAY + " Order\n");
                deal.setId(orderResponse.getResult().getOrderId());
                currentDealId = deal.getId();

                // Сохраняем сделку ДО активации
                activeDealStore.addDeal(deal);

                // Если это МАРКЕТ-ордер — позиция уже открыта → активируем сразу
                if (deal.getEntryType() == EntryType.MARKET) {
                    goIfDealOpen(chatId, deal, orderResponse);
                }
                // Если это ЛИМИТ — ждём исполнения, активация будет позже
                else {
                    messageSender.send(chatId," Лимитный ордер выставлен. Ожидаем вход...");
                }
            } else {
                messageSender.sendWarn(chatId, "❌ Ошибка при создании ордера: " + orderResponse.getRetMsg(), "handleGo()");
            }
        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка при создании сделки: " + e.getMessage(), e, "handleGo()");
        }
    }
    public void goIfDealOpen(long chatId, Deal deal, BybitOrderResponse orderResponse) {
        StringBuilder result = new StringBuilder();

        try {
            // Обновляем сделку: цена входа, статус, время
            double entryPrice = extractEntryPrice(orderResponse); // из ответа
            deal.setEntryPrice(entryPrice);
            deal.setActive(true);

            // Теперь можно ставить SL и TP
            if (deal.getStopLoss() != null) {
                BybitOrderResponse slResponse = bybitOrderService.setStopLoss(deal);
                if (slResponse.isSuccess()) {
                    result.append("SL:").append(deal.getStopLoss()).append("\n");
                } else {
                    result.append(EmojiUtils.CROSS + " SL не установлен: ").append(slResponse.getRetMsg()).append("\n");
                }
            }
// УСТАРЕВШИЙ МЕТОД placePartialTakeProfits, новый подход это центр управления выходами из позиции
            bybitOrderService.placePartialTakeProfits(deal, messageSender, chatId, result, bybitMarketService);

            // Уведомляем пользователя
            messageSender.send(chatId, EmojiUtils.OKAY + " Позиция открыта! Установлены SL и TP.\n" + result);

        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка при активации сделки после входа", e, "onEntryExecuted");
        }
    }

    //Начинать отсюда!!
    private void handleTpAdd(long chatId) {
        Deal deal = getActiveDeal(chatId);
        if (deal == null) return;
        try {
            PartialExitPlan plan = partialExitPlanner.planExit(deal.getTakeProfits());
            if (plan == null || plan.getPartialExits().isEmpty()) {
                messageSender.sendWarn(chatId, "Не удалось составить план частичного выхода.", "handleTpAdd()");
                return;
            }
            StringBuilder sb = new StringBuilder("План частичного выхода:\n");
            for (var step : plan.getPartialExits()) {
                sb.append("- TP: ").append(step.getTakeProfit())
                        .append(", Процент: ").append(step.getPercentage()).append("%\n");
            }
            messageSender.send(chatId, sb.toString());
        } catch (Exception e) {
            messageSender.sendError(chatId, "Ошибка при планировании частичного выхода:", e, "handleTpAdd()");
        }
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
        cycleBreak();
    }

    private void handleUpdate(long chatId) {
        messageSender.send(chatId, "Обновление сделок из Bybit...");
        // TODO: реализовать обновление сделок из Bybit, а пока будет просто обновление информации о позициях


        messageSender.send(chatId, "🔄 Обновление сделок из Bybit...");
        for (Deal deal : activeDealStore.getAllDeals()) {
            try {
                PositionInfo pos = bybitPositionTrackerService.getPosition(deal.getSymbol().getSymbol());
                if (pos == null) {
                    // Позиция закрыта вручную
                    messageSender.send(chatId, "🗑️ Позиция " + deal.getSymbol() + " больше не активна (закрыта на бирже).");
                    activeDealStore.removeDeal(deal.getId());
                } else {
                    // Обновляем состояние
                    deal.updateFromPosition(pos); // реализуйте этот метод
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
        this.defaultStrategyName = strategyNameInput.toLowerCase(); // Приводим к нижнему регистру для единообразия
        messageSender.send(chatId, EmojiUtils.OKAY + " Стратегия по умолчанию для новых сделок установлена на: " + this.defaultStrategyName);
        LoggerUtils.logInfo("Стратегия по умолчанию изменена пользователем на " + this.defaultStrategyName);
    }
    // ------------------
}
