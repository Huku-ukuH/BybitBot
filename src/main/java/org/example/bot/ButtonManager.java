package org.example.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

//добавить кнопки которые превращают сообщение  в диалоговое окно?

/**
 * Универсальный менеджер для создания и управления кнопками в Telegram-боте.
 * Автоматически формирует клавиатуру на основе переданного списка названий кнопок.
 */
public class ButtonManager {

    /**
     * Создает и настраивает клавиатуру на основе списка названий кнопок.
     * Кнопки распределяются по строкам: по 2 кнопки в строке.
     * Если в списке нечетное количество кнопок, последняя строка будет содержать одну кнопку.
     *
     * @param buttonLabels Список строк — названий кнопок, которые нужно отобразить.
     * @return Настроенный объект ReplyKeyboardMarkup.
     */
    public ReplyKeyboardMarkup createKeyboard(List<String> buttonLabels) {
        if (buttonLabels == null || buttonLabels.isEmpty()) {
            return null; // Нет кнопок — возвращаем null, клавиатура не будет установлена.
        }

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true); // Подгоняет размер под экран
        replyKeyboardMarkup.setOneTimeKeyboard(false); // Клавиатура не исчезает после нажатия

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Распределяем кнопки по строкам (по 2 в строке)
        for (int i = 0; i < buttonLabels.size(); i += 2) {
            KeyboardRow row = new KeyboardRow();

            // Добавляем первую кнопку
            row.add(buttonLabels.get(i));

            // Проверяем, есть ли вторая кнопка в этой строке
            if (i + 1 < buttonLabels.size()) {
                row.add(buttonLabels.get(i + 1));
            }

            keyboard.add(row);
        }

        replyKeyboardMarkup.setKeyboard(keyboard);
        return replyKeyboardMarkup;
    }

    /**
     * Создает команду для полного удаления клавиатуры у пользователя.
     *
     * @return Объект ReplyKeyboardRemove для очистки клавиатуры.
     */
    public ReplyKeyboardRemove createClearKeyboard() {
        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setRemoveKeyboard(true);
        replyKeyboardRemove.setSelective(true);
        return replyKeyboardRemove;
    }
}