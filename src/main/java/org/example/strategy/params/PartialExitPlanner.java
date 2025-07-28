
package org.example.strategy.params;

import org.example.deal.dto.PartialExitPlan;
import org.example.util.ValidationUtils;
import org.example.util.ValuesUtil;
import java.util.*;

// Планировщик стандартных выходов, использующий правила из ValuesUtil
public class PartialExitPlanner {

    // Использует стандартные правила из ValuesUtil
    public PartialExitPlan planExit(List<Double> takeProfits) {
        return planExit(takeProfits, ValuesUtil.getDefaultTpExitRules());
    }

    // Использует переданные правила или стандартные из ValuesUtil, если customRules null
    public PartialExitPlan planExit(List<Double> takeProfits, Map<Integer, int[]> customRules) {
        ValidationUtils.checkNotNull(takeProfits, "Take profits list cannot be null");

        // Определяем, какие правила использовать
        Map<Integer, int[]> rulesToUse = (customRules != null) ? customRules : ValuesUtil.getDefaultTpExitRules();

        int count = takeProfits.size();
        int[] distribution = rulesToUse.get(count);

        if (distribution == null) {
            return null;
        }

        if (takeProfits.contains(null)) {
            throw new IllegalArgumentException("null in TP");
        }

        List<PartialExitPlan.ExitStep> steps = new ArrayList<>();

        for (int i = 0; i < takeProfits.size(); i++) {
            double tp = takeProfits.get(i);
            int percentage = distribution[i];
            steps.add(new PartialExitPlan.ExitStep(tp, percentage));
        }

        PartialExitPlan plan = new PartialExitPlan();
        plan.setPartialExits(steps);
        return plan;
    }

    // Возвращает копию стандартных правил из ValuesUtil
    public static Map<Integer, int[]> getDefaultExitRules() {
        return ValuesUtil.getDefaultTpExitRules(); // <-- И здесь
    }
}