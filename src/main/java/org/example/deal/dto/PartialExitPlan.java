package org.example.deal.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


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
}