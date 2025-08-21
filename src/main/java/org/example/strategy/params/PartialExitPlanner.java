
package org.example.strategy.params;

import org.example.util.ValidationUtils;
import org.example.util.ValuesUtil;
import java.util.*;

public class PartialExitPlanner {
    public List<ExitPlan.ExitStep> planExit(List<Double> takeProfits) {
        return planExit(takeProfits, ValuesUtil.getDefaultTpExitRules());
    }

    public List<ExitPlan.ExitStep> planExit(List<Double> takeProfits, Map<Integer, int[]> customRules) {
        ValidationUtils.checkNotNull(takeProfits, "Take profits list cannot be null");
        if (takeProfits.contains(null)) throw new IllegalArgumentException("null in TP");

        int count = takeProfits.size();
        int[] distribution = customRules.get(count);
        if (distribution == null || distribution.length != count) return Collections.emptyList();

        List<ExitPlan.ExitStep> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new ExitPlan.ExitStep(takeProfits.get(i), distribution[i]));
        }
        return steps;
    }
}