package org.example.deal;

import org.example.model.Symbol;
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
    public void addDeal(Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        ValidationUtils.checkNotNull(deal.getId(), "Deal ID cannot be null");
        ValidationUtils.checkNotNull(deal.getSymbol(), "Deal symbol cannot be null");

        dealsById.put(deal.getId(), deal);

        // Обновляем индекс по символу
        dealsBySymbol
                .computeIfAbsent(deal.getSymbol(), k -> ConcurrentHashMap.newKeySet())
                .add(deal);

        // Уведомляем слушателей
        onDealAddedListeners.forEach(listener -> listener.accept(deal));
    }

    /**
     * Удаляет сделку по ID.
     * @return true, если сделка была удалена
     */
    public boolean removeDeal(String id) {
        Deal deal = dealsById.remove(id);
        if (deal == null) return false;

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
        return true;
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