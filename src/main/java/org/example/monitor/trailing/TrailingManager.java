package org.example.monitor.trailing;

import org.example.bybit.dto.TickerResponse;
import org.example.bybit.service.BybitOrderService;
import org.example.deal.Deal;
import org.example.util.LoggerUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Управляет всеми видами "скользящих" (trailing) ордеров:
 * - Trailing Entry (подтягивание лимитного ордера на вход)
 * - Trailing Partial Take Profit (фиксация части прибыли при развороте)
 */
public class TrailingManager {

    private final BybitOrderService orderService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Храним активные "трейлинг-сессии"
    private final ConcurrentHashMap<String, TrailingSession> sessions = new ConcurrentHashMap<>();

    public TrailingManager(BybitOrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Запускает трейлинг для лучшей цены входа.
     * Лимитный ордер на вход будет подтягиваться за ценой.
     */
    public void startTrailingEntry(Deal deal, double trailDistance, boolean byPercent) {
        String dealId = deal.getId();
        sessions.compute(dealId, (id, oldSession) -> {
            if (oldSession != null) {
                oldSession.cancel();
            }
            TrailingSession session = new TrailingEntrySession(deal, trailDistance, byPercent, orderService);
            scheduler.scheduleAtFixedRate(session::update, 0, 5, TimeUnit.SECONDS);
            return session;
        });
    }

    /**
     * Запускает трейлинг для частичной фиксации прибыли.
     * При движении в прибыль — поднимает TP.
     * При развороте — закрывает часть позиции.
     */
    public void startTrailingPartialTp(Deal deal, double trailDistance, boolean byPercent, double exitPercentage) {
        String dealId = deal.getId();
        sessions.compute(dealId, (id, oldSession) -> {
            if (oldSession != null) {
                oldSession.cancel();
            }
            TrailingSession session = new TrailingPartialTpSession(
                    deal, trailDistance, byPercent, exitPercentage, orderService);
            scheduler.scheduleAtFixedRate(session::update, 0, 5, TimeUnit.SECONDS);
            return session;
        });
    }

    /**
     * Останавливает трейлинг для сделки.
     */
    public void stop(String dealId) {
        sessions.computeIfPresent(dealId, (id, session) -> {
            session.cancel();
            return null;
        });
    }

    /**
     * Останавливает все активные сессии (например, при завершении работы).
     */
    public void shutdown() {
        sessions.values().forEach(TrailingSession::cancel);
        sessions.clear();
        scheduler.shutdown();
    }

    // === Внутренние классы ===

    /**
     * Абстрактная сессия трейлинга.
     */
    private abstract static class TrailingSession {
        protected final Deal deal;
        protected final double trailDistance;
        protected final boolean byPercent;
        protected final BybitOrderService orderService;
        protected volatile boolean active = true;

        public TrailingSession(Deal deal, double trailDistance, boolean byPercent, BybitOrderService orderService) {
            this.deal = deal;
            this.trailDistance = trailDistance;
            this.byPercent = byPercent;
            this.orderService = orderService;
        }

        public abstract void update();

        public void cancel() {
            active = false;
        }
    }

    /**
     * Сессия: трейлинг для входа (подтягивает лимитный ордер на вход).
     */
    private static class TrailingEntrySession extends TrailingSession {
        private double highestPrice = Double.MIN_VALUE;
        private double lowestPrice = Double.MAX_VALUE;

        public TrailingEntrySession(Deal deal, double trailDistance, boolean byPercent, BybitOrderService orderService) {
            super(deal, trailDistance, byPercent, orderService);
        }

        @Override
        public void update() {
            if (!active || deal.getEntryType() != EntryType.LIMIT || deal.isActive()) return;

            TickerResponse price = getCurrentPrice();
            if (price == null) return;

            double currentPrice = extractPrice(price);
            if (deal.getDirection() == Direction.LONG) {
                highestPrice = Math.max(highestPrice, currentPrice);
                double newPrice = byPercent
                        ? highestPrice * (1 - trailDistance / 100.0)
                        : highestPrice - trailDistance;
                if (newPrice > deal.getEntryPrice()) {
                    deal.setEntryPrice(newPrice);
                    orderService.updateEntryOrder(deal); // обновляет ордер на бирже
                }
            } else { // SHORT
                lowestPrice = Math.min(lowestPrice, currentPrice);
                double newPrice = byPercent
                        ? lowestPrice * (1 + trailDistance / 100.0)
                        : lowestPrice + trailDistance;
                if (newPrice < deal.getEntryPrice()) {
                    deal.setEntryPrice(newPrice);
                    orderService.updateEntryOrder(deal);
                }
            }
        }
    }

    /**
     * Сессия: трейлинг для частичного выхода.
     */
    private static class TrailingPartialTpSession extends TrailingSession {
        private final double exitPercentage;
        private double highestPrice = Double.MIN_VALUE;
        private double lowestPrice = Double.MAX_VALUE;
        private String activeOrderId = null;

        public TrailingPartialTpSession(Deal deal, double trailDistance, boolean byPercent,
                                        double exitPercentage, BybitOrderService orderService) {
            super(deal, trailDistance, byPercent, orderService);
            this.exitPercentage = exitPercentage;
        }

        @Override
        public void update() {
            if (!active || !deal.isActive()) return;

            TickerResponse price = getCurrentPrice();
            if (price == null) return;

            double currentPrice = extractPrice(price);

            if (deal.getDirection() == Direction.LONG) {
                highestPrice = Math.max(highestPrice, currentPrice);
                double triggerPrice = byPercent
                        ? highestPrice * (1 - trailDistance / 100.0)
                        : highestPrice - trailDistance;

                if (currentPrice <= triggerPrice) {
                    // Цена упала до уровня → фиксируем часть
                    orderService.placePartialExit(deal, exitPercentage, "Trailing TP");
                    stop(deal.getId()); // останавливаем после срабатывания
                } else if (activeOrderId == null || triggerPrice > getTriggerPriceOf(activeOrderId)) {
                    // Обновляем условный ордер
                    orderService.updateTrailingTpOrder(deal, triggerPrice, exitPercentage);
                }
            } else { // SHORT
                lowestPrice = Math.min(lowestPrice, currentPrice);
                double triggerPrice = byPercent
                        ? lowestPrice * (1 + trailDistance / 100.0)
                        : lowestPrice + trailDistance;

                if (currentPrice >= triggerPrice) {
                    orderService.placePartialExit(deal, exitPercentage, "Trailing TP");
                    stop(deal.getId());
                } else if (activeOrderId == null || triggerPrice < getTriggerPriceOf(activeOrderId)) {
                    orderService.updateTrailingTpOrder(deal, triggerPrice, exitPercentage);
                }
            }
        }
    }

    // Вспомогательные методы (реализуйте под вашу систему)
    private TickerResponse getCurrentPrice() {
        // Например: bybitMarketService.getTicker(deal.getSymbol())
        return null;
    }

    private double extractPrice(TickerResponse ticker) {
        // Извлечение lastPrice
        return 0.0;
    }

    private double getTriggerPriceOf(String orderId) {
        // Получить текущий triggerPrice ордера с биржи
        return 0.0;
    }
}