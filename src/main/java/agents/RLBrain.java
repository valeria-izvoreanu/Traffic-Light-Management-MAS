package agents;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RLBrain {
    // Hyperparameters
    private double alpha = 0.2;   // Learning Rate
    private double gamma = 0.8;   // Discount Factor
    private double epsilon = 0.15; // Exploration Rate

    // Q-Table storage
    private final Map<Integer, double[]> qTable = new HashMap<>();
    private final Random rnd = new Random(42);

    // Actions
    public static final int ACTION_SWITCH = 0;
    public static final int ACTION_HOLD_SHORT = 1; // 5s
    public static final int ACTION_HOLD_LONG = 2;  // 20s
    public static final int ACTION_COUNT = 3;

    public RLBrain() {

    }

    private double[] ensureState(int state) {
        return qTable.computeIfAbsent(state, s -> new double[ACTION_COUNT]);
    }

    public int chooseAction(int state) {
        // Epsilon decay
        if (epsilon > 0.01) epsilon *= 0.999;

        double[] qvals = ensureState(state);

        // Explore
        if (rnd.nextDouble() < epsilon) {
            return rnd.nextInt(ACTION_COUNT);
        }

        // Find Max Q
        int bestAction = 0;
        double bestQ = qvals[0];
        for (int i = 1; i < ACTION_COUNT; i++) {
            if (qvals[i] > bestQ) {
                bestQ = qvals[i];
                bestAction = i;
            }
        }
        return bestAction;
    }

    public void update(int prevState, int action, double reward, int newState) {
        double[] oldQ = ensureState(prevState);
        double[] nextQ = ensureState(newState);

        // Find max Q for next state
        double maxNext = nextQ[0];
        for(int i = 1; i < ACTION_COUNT; i++) {
            maxNext = Math.max(maxNext, nextQ[i]);
        }

        // Bellman Equation
        oldQ[action] = oldQ[action] + alpha * (reward + gamma * maxNext - oldQ[action]);
    }

    public void printQTable() {
        System.out.println("\nFINAL Q-TABLE REPORT");
        if (qTable.isEmpty()) {
            System.out.println("Q-Table is empty");
            return;
        }

        java.util.List<Integer> sortedStates = new java.util.ArrayList<>(qTable.keySet());
        java.util.Collections.sort(sortedStates);

        for (Integer state : sortedStates) {
            double[] actions = qTable.get(state);

            // Decode state
            int bucketNS = (state >> 3) & 0b11;
            int bucketEW = (state >> 1) & 0b11;
            int light = state & 1;

            // Interpret buckets
            String nsStr = bucketToText(bucketNS);
            String ewStr = bucketToText(bucketEW);
            String lightStr = (light == 1) ? "GREEN_NS" : "GREEN_EW";

            // Find best action
            int bestAction = 0;
            if (actions[1] > actions[bestAction]) bestAction = 1;
            if (actions[2] > actions[bestAction]) bestAction = 2;
            String bestActStr = actionToText(bestAction);

            System.out.printf("State [NS:%-6s | EW:%-6s | %-8s] -> Switch: %6.1f | HoldShort: %6.1f | HoldLong: %6.1f  >>> BEST: %s%n",
                    nsStr, ewStr, lightStr, actions[0], actions[1], actions[2], bestActStr);
        }
        System.out.println("----------------------------\n");
    }

    private String bucketToText(int b) {
        if (b == 0) return "Empty";
        if (b == 1) return "Light";
        if (b == 2) return "Med";
        return "HEAVY";
    }

    private String actionToText(int a) {
        if (a == 0) return "SWITCH";
        if (a == 1) return "HOLD_5s";
        return "HOLD_20s";
    }
}