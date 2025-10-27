package org.example.deal.utils;

import org.example.deal.Deal;
import org.example.model.Symbol;
import org.example.result.OperationResult;
import org.example.util.ValidationUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Хранилище активных сделок.
 * Обеспечивает потокобезопасный доступ, поддержку событий и эффективный поиск по символу.
 */
public class ActiveDealStore {
    // Основное хранилище: id -> Deal
    private final Map<String, Deal> dealsById = new ConcurrentHashMap<>();

    // Индекс для быстрого поиска: symbol -> Set<Deal>
    private final Map<Symbol, Set<Deal>> dealsBySymbol = new ConcurrentHashMap<>();

    // Слушатели событий (например, PriceMonitor)
    private final List<Consumer<Deal>> onDealAddedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Deal>> onDealRemovedListeners = new CopyOnWriteArrayList<>();

    // --- Управление сделками ---

    /**
     * Добавляет новую сделку.
     * Уведомляет всех подписчиков.
     */
    public OperationResult addDeal(Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        ValidationUtils.checkNotNull(deal.getId(), "Deal ID cannot be null");
        ValidationUtils.checkNotNull(deal.getSymbol(), "Deal symbol cannot be null");

        // 1. Если уже есть по ID — не добавляем
        if (dealsById.containsKey(deal.getId())) {
            return OperationResult.failure("Айди Deal " + deal.getSymbol() + " попытка повторного добавления в ActiveDealStore отклонена");
        }

        // 2. Если уже есть активная сделка по символу — не добавляем
        Set<Deal> existingDeals = dealsBySymbol.get(deal.getSymbol());
        if (existingDeals != null && !existingDeals.isEmpty()) {
            // Можно дополнительно проверить, что хотя бы одна активна
            boolean hasActive = existingDeals.stream().anyMatch(Deal::isActive);
            if (hasActive) {
                return OperationResult.failure("Активная Deal " + deal.getSymbol() + "  попытка повторного добавления в ActiveDealStore отклонена");
            }
        }

        // 3. Добавляем
        dealsById.put(deal.getId(), deal);
        dealsBySymbol
                .computeIfAbsent(deal.getSymbol(), k -> ConcurrentHashMap.newKeySet())
                .add(deal);

        onDealAddedListeners.forEach(listener -> listener.accept(deal));
        return OperationResult.success(); // успешно добавлено
    }

    /**
     * Удаляет сделку по ID.
     * @return true, если сделка была удалена
     */
    public OperationResult removeDeal(String id) {
        Deal deal = dealsById.remove(id);
        if (deal == null) {
            return OperationResult.failure("Невозможно удалить Deal из ActiveDealStore, Deal == null");
        }

        // Удаляем из индекса по символу
        Set<Deal> deals = dealsBySymbol.get(deal.getSymbol());
        if (deals != null) {
            deals.remove(deal);
            if (deals.isEmpty()) {
                dealsBySymbol.remove(deal.getSymbol());
            }
        }
        // Уведомляем об удалении
        onDealRemovedListeners.forEach(listener -> listener.accept(deal));
        return OperationResult.success();
    }


    // --- Получение данных ---

    /**
     * Возвращает все активные сделки.
     * Результат — копия, чтобы избежать внешних модификаций.
     */
    public List<Deal> getAllDeals() {
        return new ArrayList<>(dealsById.values());
    }

    /**
     * Возвращает сделки по символу.
     * Результат — неизменяемая копия.
     */
    public List<Deal> getDealsBySymbol(Symbol symbol) {
        Set<Deal> deals = dealsBySymbol.get(symbol);
        return deals == null ? Collections.emptyList() : new ArrayList<>(deals);
    }

    /**
     * Возвращает сделку по ID.
     */
    public Deal getDealById(String id) {
        return dealsById.get(id);
    }

    /**
     * Проверяет, существует ли сделка.
     */
    public boolean containsDeal(String id) {
        return dealsById.containsKey(id);
    }

    /**
     * Возвращает количество активных сделок.
     */
    public int size() {
        return dealsById.size();
    }

    // --- Управление жизненным циклом ---

    /**
     * Удаляет все неактивные сделки.
     * @return количество удалённых сделок
     */
    public int removeCompletedDeals() {
        List<Deal> completed = new ArrayList<>();
        for (Deal deal : dealsById.values()) {
            if (!deal.isActive()) {
                completed.add(deal);
            }
        }

        // Удаляем каждую завершённую сделку
        completed.forEach(deal -> removeDeal(deal.getId()));
        return completed.size();
    }

    /**
     * Полная очистка хранилища (для тестов)
     */
    public void clear() {
        dealsById.clear();
        dealsBySymbol.clear();
    }

    // --- События (Event Listeners) ---

    /**
     * Добавляет слушателя на событие "сделка добавлена"
     */
    public void addOnDealAddedListener(Consumer<Deal> listener) {
        onDealAddedListeners.add(listener);
    }

    /**
     * Добавляет слушателя на событие "сделка удалена"
     */
    public void addOnDealRemovedListener(Consumer<Deal> listener) {
        onDealRemovedListeners.add(listener);
    }

    /**
     * Удаляет слушателя
     */
    public void removeOnDealAddedListener(Consumer<Deal> listener) {
        onDealAddedListeners.remove(listener);
    }

    public void removeOnDealRemovedListener(Consumer<Deal> listener) {
        onDealRemovedListeners.remove(listener);
    }
}