package agents;

public class MicroStateEncoder {

    public MicroStateEncoder() {
    }

    public int encodeState(int qNS, int qEW, boolean greenNS) {
        // Define NS traffic weight
        int bucketNS;
        if (qNS == 0) bucketNS = 0;       // Empty
        else if (qNS <= 6) bucketNS = 1;  // Light
        else if (qNS <= 15) bucketNS = 2; // Medium
        else bucketNS = 3;                // Heavy

        // Define EW traffic weight
        int bucketEW;
        if (qEW == 0) bucketEW = 0;
        else if (qEW <= 6) bucketEW = 1;
        else if (qEW <= 15) bucketEW = 2;
        else bucketEW = 3;

        // Light Status
        int light = greenNS ? 1 : 0;

        // Combine into unique integer
        return (bucketNS << 3) | (bucketEW << 1) | light;
    }

    public double calculateReward(int qNS, int qEW, boolean didSwitch) {
        // Base penalty: total cars waiting
        double reward = -(qNS + qEW);

        // Extra penalty: If Highway is blocked, it hurts 2x more
        if (qNS > 0) reward -= (qNS * 2.0);

        // Switching cost (Yellow light penalty)
        // To prevent agent from flickering the lights too fast
        if (didSwitch) reward -= 10.0;

        return reward;
    }
}