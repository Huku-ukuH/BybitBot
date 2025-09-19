package org.example.bot;

import lombok.Getter;
import lombok.Setter;
import org.example.ai.AiService;
import org.example.bybit.BybitManager;
import org.example.deal.ActiveDealStore;
import org.example.deal.Deal;
import org.example.deal.DealCalculator;
import org.example.deal.UpdateManager;
import org.example.deal.dto.DealRequest;
import org.example.deal.dto.DealValidationResult;
import org.example.model.Direction;
import org.example.model.EntryType;
import org.example.monitor.dto.PositionInfo;
import org.example.strategy.strategies.strategies.StrategyFactory;
import org.example.util.EmojiUtils;
import org.example.util.LoggerUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


@Getter
@Setter
public class BotCommandHandler {
    private BybitManager bybitManager;
    private final ActiveDealStore activeDealStore;
    private String strategyName = "ai";
    private boolean waitingSignal = false;
    private  UpdateManager updateManager;
    private MessageSender messageSender;
    private final AiService aiService;
    private boolean justChat = false;
    private DealRequest dealRequest;
    private String currentDealId;
    private Deal deal;
    // -----------------

    public BotCommandHandler(BybitManager bybitManager, AiService aiService, ActiveDealStore activeDealStore, MessageSender messageSender, UpdateManager updateManager) {
        this.activeDealStore = activeDealStore;
        this.updateManager = updateManager;
        this.messageSender = messageSender;
        this.bybitManager = bybitManager;
        this.aiService = aiService;
    }
    public void handleCommand(long chatId, String command, String messageText) {
        if (updateManager.isCreateDealsProcess()) {
            handleUpdateDeals(chatId, messageText);
        }

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
            case "/update" -> handleUpdateDeals(chatId, "defaultValue");
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

            try {
                messageSender.sendError(chatId, "Ошибка обработки сигнала, пробуем снова", e, "handleGetSignal()");
                deal = StrategyFactory.getStrategy(strategyName).createDeal(aiService, messageText, chatId, strategyName);
                currentDealId = deal.getId();
                messageSender.send(chatId, deal.toString());
                waitingSignal = false;
            } catch (Exception a) {
                messageSender.sendError(chatId, "Не помогло :(", e, "handleGetSignal()");
                cycleBreak(chatId);
                waitingSignal = false;
            }
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
            messageSender.sendWarn(chatId, "Сделки нет!", "handleGo()");
            return;
        }

        try {
            if (deal.getStrategy().openDeal(bybitManager.getBybitOrderService(), deal)) {
                currentDealId = deal.getId();
                activeDealStore.addDeal(deal);

                if (deal.getEntryType() == EntryType.MARKET) {
                    String result;
                    try {
                        result = deal.getStrategy().goIfDealOpen(deal, bybitManager);
                    } catch (Exception e) {
                        messageSender.sendError(chatId, "Ошибка при активации сделки", e, "handleGo()");
                        cycleBreak(chatId);
                        return;
                    }

                    messageSender.send(
                            chatId,
                            EmojiUtils.OKAY + " Сделка открыта!\n" +
                                    deal.bigDealToString() + "\n" +
                                    result
                    );
                } else {
                    messageSender.send(chatId, "🕒 Лимитный ордер выставлен. Ожидаем вход...");
                }
            } else {
                messageSender.sendWarn(chatId, "❌ Ордер не был размещён.", "handleGo()");
                cycleBreak(chatId);
            }
        } catch (Exception e) {
            messageSender.sendError(chatId, "❌ Ошибка при открытии сделки", e, "handleGo()");
            cycleBreak(chatId);
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
        cycleBreak(chatId);
    }


       // todo: ПРОДОЛЖАТЬ ОТСЮДА
  //метод обновления, а точнее метод восстановления сделок после перезагрузки бота,
    // но пока это просто метод для обновления информации о сделках
       private void handleUpdateDeals(long chatId, String strategyName) {
           messageSender.send(chatId, "🔄 Обновление сделок из Bybit... Находится в разработке! Предлагаю заняться наладкой реакции на обновление цены от вебсокета");

           try {
               String updateResult = updateManager.updateDeals(bybitManager, activeDealStore, chatId, strategyName);

               if (updateManager.isCreateDealsProcess()) {
                   List<String> strategyButtons = Arrays.asList(StrategyFactory.getAvailableStrategies().toArray(new String[0]));
                   // ✅ Отправляем сообщение С КЛАВИАТУРОЙ
                   messageSender.sendWithButtons(chatId, updateResult, strategyButtons);
                   return;
               }

               // ✅ Отправляем сообщение и ОЧИЩАЕМ клавиатуру (на случай, если она была)
               messageSender.sendAndClearButtons(chatId, updateResult);

           } catch (Exception e) {
               LoggerUtils.logError("Надо же, ошибка", e);
               messageSender.sendError(chatId, "Произошла ошибка при обновлении", e, "handleUpdateDeals");
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
