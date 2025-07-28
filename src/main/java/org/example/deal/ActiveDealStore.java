package org.example.deal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.example.model.Symbol;
import org.example.util.ValidationUtils;

//Цель:
//Хранить все активные сделки (Deal) в памяти, обеспечивать доступ к ним по ID или символу, управлять их жизненным циклом.
//
//Функционал:
//Добавление новой сделки
//Поиск сделок по ID или символу
//Обновление данных сделки
//Удаление сделки
//Получение списка всех активных сделок
public class ActiveDealStore {
    // Хранилище активных сделок: id -> Deal
    private final Map<String, Deal> activeDeals = new ConcurrentHashMap<>();

    /**
     * Добавляет новую сделку в хранилище
     */
    public void addDeal(Deal deal) {
        ValidationUtils.checkNotNull(deal, "Deal cannot be null");
        activeDeals.put(deal.getId(), deal);
    }

    /**
     * Возвращает список всех активных сделок
     */
    public List<Deal> getAllDeals() {
        return new ArrayList<>(activeDeals.values());
    }

    /**
     * Возвращает список сделок по символу
     */
    public List<Deal> getDealsBySymbol(Symbol symbol) {
        List<Deal> result = new ArrayList<>();
        for (Deal deal : activeDeals.values()) {
            if (deal.getSymbol().equals(symbol)) {
                result.add(deal);
            }
        }
        return result;
    }

    /**
     * Обновляет существующую сделку
     */
    public boolean updateDeal(Deal updatedDeal) {
        if (!activeDeals.containsKey(updatedDeal.getId())) {
            return false;
        }
        activeDeals.put(updatedDeal.getId(), updatedDeal);
        return true;
    }

    /**
     * Удаляет сделку по ID
     */
    public boolean removeDeal(String id) {
        return activeDeals.remove(id) != null;
    }

    /**
     * Удаляет все завершённые сделки (неактивные)
     */
    public int removeCompletedDeals() {
        int count = 0;
        Iterator<Deal> iterator = activeDeals.values().iterator();
        while (iterator.hasNext()) {
            Deal deal = iterator.next();
            if (!deal.isActive()) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    /**
     * Проверяет, существует ли сделка
     */
    public boolean containsDeal(String id) {
        return activeDeals.containsKey(id);
    }

    /**
     * Возвращает количество активных сделок
     */
    public int size() {
        return activeDeals.size();
    }

    /**
     * Очищает хранилище (для тестов или перезапуска)
     */
    public void clear() {
        activeDeals.clear();
    }
}