package org.example.strategy.strategies.strategies;
import org.example.strategy.strategies.strategies.superStrategy.AbstractStrategy;
import org.example.util.LoggerUtils;
import java.util.ArrayList;
import java.util.List;


//Фабрика для создания экземпляров торговых стратегий.
public class StrategyFactory {

    private static final List<String> AVAILABLE_STRATEGIES = List.of("ai", "free" /*,"new"*/);      // добавить имя сюда


    //Получает экземпляр стратегии по её имени.
    public static AbstractStrategy getStrategy(String name) throws IllegalArgumentException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Имя стратегии не может быть null или пустым.");
        }

        return switch (name.toLowerCase()) {
            case "ai" -> new BasedStrategy();
            case "free" -> new FreeStrategy();
            //case "new" -> new NewStrategy();                                                      // добавить имя сюда
            default -> {
                LoggerUtils.warn("Запрошена неизвестная стратегия: " + name);
                throw new IllegalArgumentException("Неизвестная стратегия: " + name);
            }
        };
    }

    //Проверяет, доступна ли стратегия с заданным именем.
    public static boolean isStrategyAvailable(String name) {
        return name != null && !name.isEmpty() && AVAILABLE_STRATEGIES.contains(name.toLowerCase());
    }

    //Получает список всех доступных стратегий.
    public static List<String> getAvailableStrategies() {
        // Возвращаем неизменяемую копию, чтобы предотвратить модификацию извне
        return new ArrayList<>(AVAILABLE_STRATEGIES);
    }
}