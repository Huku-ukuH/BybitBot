package org.example.deal.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Getter
@Setter
@NoArgsConstructor


//План частичного выхода по TP
public class PartialExitPlan {
    private List<ExitStep> partialExits;

    @Override
    public String toString() {
        return "PartialExitPlan{" +
                "partialExits=" + partialExits +
                '}';
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor

    public static class ExitStep {
        private double takeProfit;
        private int percentage;

        @Override
        public String toString() {
            return "ExitStep{" +
                    "takeProfit=" + takeProfit +
                    ", percentage=" + percentage +
                    '}';
        }
    }
    public enum ExitType {
        TP,         // по уровням из сигнала
        PNL,        // по достижению прибыли
        TRAILING    // по трейлингу
    }

    @Getter
    public class ExitPlan {
        private final List<ExitStep> steps;
        private final ExitType type;

        public ExitPlan(List<ExitStep> steps, ExitType type) {
            this.steps = new ArrayList<>(steps);
            this.type = type;
        }

        public ExitPlan fromTp(List<Double> takeProfits, Map<Integer, int[]> rules) {
            List<ExitStep> steps = new ArrayList<>();
            for (int i = 0; i < takeProfits.size(); i++) {
                steps.add(new ExitStep(takeProfits.get(i), rules.get(takeProfits.size())[i]));
            }
            return new ExitPlan(steps, ExitType.TP);
        }

        public ExitPlan fromPnl(List<Double> levels, List<Integer> percentages) {
            List<ExitStep> steps = new ArrayList<>();
            for (int i = 0; i < levels.size(); i++) {
                steps.add(new ExitStep(levels.get(i), percentages.get(i)));
            }
            return new ExitPlan(steps, ExitType.PNL);
        }
    }
}