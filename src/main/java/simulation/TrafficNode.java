package simulation;

import java.util.Random;

public class TrafficNode {

    private String id;
    private int x, y;

    private int carsNorthSouth = 0;
    private int carsEastWest = 0;
    private boolean greenForNorthSouth = true;

    private int currentThreshold = 5;
    private boolean resetRequested = false;
    private int transitionTimer = 0;
    private double congestionPheromone = 0.0;

    // Metrics
    private int totalCarsPassed = 0;
    private long cumulativeQueueSum = 0;
    private long ticksCount = 0;
    private long totalWaitTime = 0;
    private double totalCO2 = 0.0;

    // Event Flags
    private boolean sensorsWorking = true;
    private boolean hasAmbulance = false;

    private Random random = new Random(42);

    public TrafficNode(String id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public void reset() {
        carsNorthSouth = 0;
        carsEastWest = 0;

        totalCarsPassed = 0;
        cumulativeQueueSum = 0;
        ticksCount = 0;
        totalWaitTime = 0;
        totalCO2 = 0.0;

        greenForNorthSouth = true;
        currentThreshold = 5;

        resetRequested = true;
        transitionTimer = 0;
        congestionPheromone = 0.0;

        sensorsWorking = true;
        hasAmbulance = false;

        random = new Random(42);
    }

    // Physics
    // Streets are one directional N->S and E->W for
    public void generateTrafficFlow() {
        if (random.nextInt(100) < CityMap.getInstance().getHighwayProb()){
            int nsBatch = 1 + random.nextInt(3);
            carsNorthSouth += nsBatch;
        }

        if (random.nextInt(100) < CityMap.getInstance().getSideStreetProb()) {
            int ewBatch = 1 + random.nextInt(3);
            carsEastWest += ewBatch;
        }

        // Metrics
        cumulativeQueueSum += (carsNorthSouth + carsEastWest);
        ticksCount++;
        int currentQueue = carsNorthSouth + carsEastWest;
        totalWaitTime += currentQueue;
        totalCO2 += (currentQueue * 1.0);
    }
    private int maxCapacity = 20; // Max cars allowed on a road segment

    public boolean addCarsNorthSouth(int amount) {
        if (carsNorthSouth + amount > maxCapacity) {
            return false; // Road is full
        }
        this.carsNorthSouth += amount;
        cumulativeQueueSum += amount;
        ticksCount++;
        return true;
    }

    public boolean addCarsEastWest(int amount) {
        if (carsEastWest + amount > maxCapacity) {
            return false; // Road is full
        }
        carsEastWest += amount;
        cumulativeQueueSum += amount;
        ticksCount++;
        return true;
    }

    public int processFlow() {

        if (transitionTimer > 0) {
            transitionTimer--;
            return 0;
        }

        int moved = 0;

        if (greenForNorthSouth) {

            // Normally a 1 vehicle per tick moves in the intersection
            // If ambulance is present, other cars pull over so the ambulance
            // can move as fast as possible in the case of emergency
            int speed = hasAmbulance ? 500 : 1;

            for (int i = 0; i < speed; i++) {
                if (carsNorthSouth > 0) {
                    carsNorthSouth--;
                    moved++;
                }
            }

            // Ambulance leaves if cleared
            if (hasAmbulance && carsNorthSouth < 2) {
                hasAmbulance = false;
            }
        }

        else {
            if (carsEastWest > 0) {
                carsEastWest--;
                moved++;
            }
        }

        totalCarsPassed += moved;
        return moved;
    }

    public void switchLight() {
        greenForNorthSouth = !greenForNorthSouth;

        if (CityMap.getInstance().isPenaltyEnabled()) {
            transitionTimer = 2; // yellow
            // Penalty for when colour is switched
            // In VT-Micro Emission Mode more fuel is consumed at Acceleration
            // than while idle or accelerating
            totalCO2 += (carsNorthSouth + carsEastWest) * 2.0;
        }
    }

    // Stigmergy
    public void updatePheromones() {
        if (congestionPheromone > 0) congestionPheromone -= 0.5;

        int total = carsNorthSouth + carsEastWest;

        if (total > 15) congestionPheromone += 2.0;
        else if (total > 10) congestionPheromone += 1.0;

        if (congestionPheromone < 0) congestionPheromone = 0;
        if (congestionPheromone > 10) congestionPheromone = 10;
    }

    // God Mode (for UI purposes)
    public int getRealQueueNS() { return carsNorthSouth; }
    public int getRealQueueEW() { return carsEastWest; }

    // Events
    public void toggleSensors() { sensorsWorking = !sensorsWorking; }

    public void addAmbulance() {
        hasAmbulance = true;
        carsNorthSouth++;
    }

    public boolean checkAndClearReset() {
        if (resetRequested) {
            resetRequested = false;
            return true;
        }
        return false;
    }

    public String getId() {
        return id;
    }
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }

    public int getQueueNS() {
        return sensorsWorking ? carsNorthSouth : -1;
    }
    public int getQueueEW() {
        return sensorsWorking ? carsEastWest : -1;
    }

    public boolean areSensorsWorking() {
        return sensorsWorking;
    }

    public boolean hasAmbulance() {
        return sensorsWorking && hasAmbulance;
    }

    public boolean isNsGreen() {
        return greenForNorthSouth;
    }

    public int getTotalPassed() {
        return totalCarsPassed;
    }

    public double getAverageQueue() {
        return (ticksCount == 0) ? 0.0 : (double) cumulativeQueueSum / ticksCount;
    }

    public double getAvgWaitTime() {
        return (totalCarsPassed == 0) ? 0.0 : (double) totalWaitTime / totalCarsPassed;
    }

    public double getTotalCO2() {
        return totalCO2; }


    public void setThreshold(int t) {
        currentThreshold = t;
    }
    public int getThreshold() {
        return currentThreshold;
    }

    public double getPheromoneLevel() {
        return congestionPheromone;
    }

    public boolean isInTransition() {
        return transitionTimer > 0;
    }

    public long getTotalWaitTimeRaw() {
        return this.totalWaitTime;
    }
}
