package simulation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CityMap {

    // Singleton
    private static CityMap instance;

    // All intersections
    private final Map<String, TrafficNode> intersections = new ConcurrentHashMap<>();

    // Control Flags
    private boolean simulationRunning = false;
    private boolean penaltyEnabled = true;

    private long ambulanceStartTime = 0;
    private double finalAmbulanceTime = 0.0;

    public enum Mode {
        BASELINE_FIXED,
        LEARNING_ONLY,
        COORD_GREEN_WAVE,
        COORD_STIGMERGY,
        SMART
    }

    private Mode currentMode = Mode.BASELINE_FIXED;

    private CityMap() {}

    public static synchronized CityMap getInstance() {
        if (instance == null) {
            instance = new CityMap();
        }
        return instance;
    }

    public void addIntersection(String name, TrafficNode node) {
        intersections.put(name, node);
    }

    public TrafficNode getIntersection(String name) {
        return intersections.get(name);
    }

    public Map<String, TrafficNode> getAllIntersections() {
        return intersections;
    }

    public boolean isSimulationRunning() {
        return simulationRunning;
    }

    public void setSimulationRunning(boolean running) {
        this.simulationRunning = running;
    }

    public boolean isPenaltyEnabled() {
        return penaltyEnabled;
    }

    public void setPenaltyEnabled(boolean enabled) {
        this.penaltyEnabled = enabled;
    }

    public void setMode(Mode m) {
        this.currentMode = m;
    }

    public Mode getMode() {
        return this.currentMode;
    }

    // Reset entire simulation
    public void resetAll() {
        for (TrafficNode node : intersections.values()) {
            node.reset();
        }
        ambulanceStartTime = 0;
        finalAmbulanceTime = 0.0;
    }

    // Special Events
    public void toggleSensorFailure(String id) {
        TrafficNode n = intersections.get(id);
        if (n != null) n.toggleSensors();
    }

    public void spawnAmbulance(String id) {
        TrafficNode n = intersections.get(id);
        if (n != null) n.addAmbulance();
    }

    public void startAmbulanceTimer() {
        this.ambulanceStartTime = System.currentTimeMillis();
        this.finalAmbulanceTime = 0.0;
    }

    // Called when ambulance leaves OR simulation ends
    public void stopAmbulanceTimer() {
        if (ambulanceStartTime > 0) {
            long duration = System.currentTimeMillis() - ambulanceStartTime;
            finalAmbulanceTime = duration / 1000.0;
            ambulanceStartTime = 0;
        }
    }

    public double getAmbulanceTotalTime() {
        if (ambulanceStartTime > 0) {
            return (System.currentTimeMillis() - ambulanceStartTime) / 1000.0;
        }
        return finalAmbulanceTime;
    }

    private int highwayProb = 50;
    private int sideStreetProb = 15;

    public void setTrafficParams(int h, int s) {
        this.highwayProb = h;
        this.sideStreetProb = s;
    }
    public int getHighwayProb() { return highwayProb; }
    public int getSideStreetProb() { return sideStreetProb; }

    // Log Metrics
    private PrintWriter csvWriter;

    public void initCSV(String filename) {
        try {
            csvWriter = new PrintWriter(new FileWriter(filename));
            csvWriter.println("Time,Mode,TotalThroughput,AvgWaitTime,TotalCO2");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void logToCSV(double time, double avgWait, double throughput, double co2) {
        if (csvWriter != null) {
            csvWriter.printf("%.2f,%s,%.2f,%.2f,%.2f%n",
                    time, currentMode.toString(), throughput, avgWait, co2);
            csvWriter.flush(); // Ensure data is written
        }
    }
}
