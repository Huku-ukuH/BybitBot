package org.example.deal.dto;

import lombok.Getter;
import java.util.List;

@Getter
public class DealValidationResult {
    private final List<String> warnings;
    private final List<String> errors;

    public DealValidationResult(List<String> warnings, List<String> errors) {
        this.warnings = warnings;
        this.errors = errors;
    }

    public StringBuilder formatWarnings() {
        StringBuilder sb = new StringBuilder("Результат проверки сделки:\n");

        if (warnings.isEmpty()) {
            sb.append("✅ Сделка не содержит ошибок.\n");
            return sb;
        }

       sb.append("\n\u26A0\uFE0F Предупреждения:\n");
       for (String warning : warnings) {
           sb.append(" - ").append(warning).append("\n");
       }

        return sb;
    }
    public StringBuilder formatErrors() {

        if (errors.isEmpty()) {
            return new StringBuilder();
        }

        StringBuilder sb = new StringBuilder(" Обнаружены критические ошибки. Проверьте вводные\n");
        sb.append("\n\uD83D\uDEA8 Ошибки:\n");
        for (String error : errors) {
            sb.append(" - ").append(error).append("\n");
        }
        return sb;

    }
}
