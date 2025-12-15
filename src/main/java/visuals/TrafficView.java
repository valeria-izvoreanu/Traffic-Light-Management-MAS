package visuals;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import simulation.CityMap;
import simulation.TrafficNode;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class TrafficView extends Application {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 780;

    private static final int GRID_SIZE = 3;
    private static final int SPACING = 200;
    private static final int OFFSET = 150;

    private static final int ROAD_W = 60;
    private static final int CAR_SIZE = 14;
    private static final int CAR_SPACING = 20;

    private double currentTime = 0;
    private double maxTime = 60.0;
    private boolean eventsTriggered = false;

    // METRIC SNAPSHOTS
    private double lastSnapshotTime = 0;
    private long lastTotalWait = 0;
    private int lastTotalPassed = 0;

    // LIVE METRIC
    private double liveAvgWait = 0.0;

    // --- CLASS FIELDS (Accessible everywhere) ---
    private TextField tfHighProb;
    private TextField tfSideProb;
    private RadioButton rbFailCorner;
    private RadioButton rbFailCenter;
    private RadioButton rbAmb;
    private RadioButton rbNone;
    // --------------------------------------------

    @Override
    public void start(Stage primaryStage) {

        BorderPane root = new BorderPane();
        Canvas canvas = new Canvas(WIDTH, HEIGHT - 100);
        root.setCenter(canvas);

        // --------------------------------------------------------
        // MAIN CONTROL BAR CONTAINER
        // --------------------------------------------------------
        HBox controls = new HBox(20);
        controls.setPadding(new Insets(10));
        controls.setPrefHeight(100);
        controls.setAlignment(Pos.CENTER);
        controls.setStyle("-fx-background-color: #333;");

        // --------------------------------------------------------
        // GROUP 1: SETTINGS (Mode & Penalty)
        // --------------------------------------------------------
        VBox groupSettings = new VBox(10);
        groupSettings.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> cbMode = new ComboBox<>();
        cbMode.getItems().addAll(
                "1. Baseline", "2. Learning Only", "3. Green Wave",
                "4. Stigmergy", "5. Smart (Full)"
        );
        cbMode.getSelectionModel().select(0);
        cbMode.setPrefWidth(140);

        CheckBox cbPenalty = new CheckBox("Yellow Light");
        cbPenalty.setSelected(true);
        cbPenalty.setStyle("-fx-text-fill: white;");

        groupSettings.getChildren().addAll(cbMode, cbPenalty);

        // --------------------------------------------------------
        // GROUP 2: TRAFFIC PARAMS (Grid Layout)
        // --------------------------------------------------------
        GridPane trafficGrid = new GridPane();
        trafficGrid.setHgap(10);
        trafficGrid.setVgap(5);
        trafficGrid.setAlignment(Pos.CENTER_LEFT);

        // Row 0: Highway
        Label lblHigh = new Label("H-Way %:");
        lblHigh.setTextFill(Color.WHITE);
        tfHighProb = new TextField("50");
        tfHighProb.setPrefWidth(40);
        trafficGrid.add(lblHigh, 0, 0);
        trafficGrid.add(tfHighProb, 1, 0);

        // Row 1: Side Street
        Label lblSide = new Label("Side %:");
        lblSide.setTextFill(Color.WHITE);
        tfSideProb = new TextField("15");
        tfSideProb.setPrefWidth(40);
        trafficGrid.add(lblSide, 0, 1);
        trafficGrid.add(tfSideProb, 1, 1);

        // --------------------------------------------------------
        // GROUP 3: SCENARIOS (2x2 Grid Layout)
        // --------------------------------------------------------
        ToggleGroup groupScenario = new ToggleGroup();
        GridPane scenarioGrid = new GridPane();
        scenarioGrid.setHgap(15);
        scenarioGrid.setVgap(5);
        scenarioGrid.setAlignment(Pos.CENTER_LEFT);

        rbNone = new RadioButton("No Event");
        rbNone.setToggleGroup(groupScenario);
        rbNone.setSelected(true);
        rbNone.setStyle("-fx-text-fill: white;");
        scenarioGrid.add(rbNone, 0, 0);

        rbFailCorner = new RadioButton("Fail Corner");
        rbFailCorner.setToggleGroup(groupScenario);
        rbFailCorner.setStyle("-fx-text-fill: orange;");
        scenarioGrid.add(rbFailCorner, 1, 0);

        rbFailCenter = new RadioButton("Fail Center");
        rbFailCenter.setToggleGroup(groupScenario);
        rbFailCenter.setStyle("-fx-text-fill: yellow;");
        scenarioGrid.add(rbFailCenter, 0, 1);

        rbAmb = new RadioButton("Ambulance");
        rbAmb.setToggleGroup(groupScenario);
        rbAmb.setStyle("-fx-text-fill: red;");
        scenarioGrid.add(rbAmb, 1, 1);

        // --------------------------------------------------------
        // GROUP 4: ACTIONS (Duration, Start, Reset, Timer)
        // --------------------------------------------------------
        VBox groupActions = new VBox(5);
        groupActions.setAlignment(Pos.CENTER);

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER);

        Label lblDur = new Label("Secs:");
        lblDur.setTextFill(Color.LIGHTGRAY);
        TextField tfDuration = new TextField("60");
        tfDuration.setPrefWidth(40);

        Button btnStart = new Button("START");
        btnStart.setStyle("-fx-font-weight: bold; -fx-base: #32CD32;");

        Button btnReset = new Button("RESET");
        btnReset.setStyle("-fx-font-weight: bold; -fx-base: #FF6347;");

        buttonRow.getChildren().addAll(lblDur, tfDuration, btnStart, btnReset);

        Label lblTimer = new Label("0.0s");
        lblTimer.setTextFill(Color.WHITE);
        lblTimer.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));

        groupActions.getChildren().addAll(buttonRow, lblTimer);

        // --------------------------------------------------------
        // ADD GROUPS TO MAIN BAR
        // --------------------------------------------------------
        controls.getChildren().addAll(
                groupSettings,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                trafficGrid,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                scenarioGrid,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                groupActions
        );

        root.setBottom(controls);

        // --------------------------------------------------------
        // BUTTON LOGIC
        // --------------------------------------------------------
        btnStart.setOnAction(e -> {
            try {
                // Parse Inputs
                maxTime = Double.parseDouble(tfDuration.getText());
                int hProb = Integer.parseInt(tfHighProb.getText());
                int sProb = Integer.parseInt(tfSideProb.getText());

                // Clamp
                hProb = Math.max(0, Math.min(100, hProb));
                sProb = Math.max(0, Math.min(100, sProb));

                CityMap.getInstance().setTrafficParams(hProb, sProb);

                switch (cbMode.getSelectionModel().getSelectedIndex()) {
                    case 0 -> CityMap.getInstance().setMode(CityMap.Mode.BASELINE_FIXED);
                    case 1 -> CityMap.getInstance().setMode(CityMap.Mode.LEARNING_ONLY);
                    case 2 -> CityMap.getInstance().setMode(CityMap.Mode.COORD_GREEN_WAVE);
                    case 3 -> CityMap.getInstance().setMode(CityMap.Mode.COORD_STIGMERGY);
                    case 4 -> CityMap.getInstance().setMode(CityMap.Mode.SMART);
                }

                CityMap.getInstance().setPenaltyEnabled(cbPenalty.isSelected());

                // --- INITIALIZE CSV ---
                String modeName = cbMode.getSelectionModel().getSelectedItem().toString().replace(" ", "_");
                String fileName = "Experiment_" + modeName + "_" + System.currentTimeMillis() + ".csv";
                CityMap.getInstance().initCSV(fileName);
                System.out.println("Saving to: " + fileName);

                CityMap.getInstance().setSimulationRunning(true);
                eventsTriggered = false;

            } catch (Exception ex) {
                tfDuration.setText("60");
                tfHighProb.setText("50");
                tfSideProb.setText("15");
                System.out.println("Invalid Input");
            }
        });

        btnReset.setOnAction(e -> {
            CityMap.getInstance().setSimulationRunning(false);
            CityMap.getInstance().resetAll();
            currentTime = 0;
            eventsTriggered = false;
            lastSnapshotTime = 0;
            lastTotalWait = 0;
            lastTotalPassed = 0;
            liveAvgWait = 0.0;
            lblTimer.setText("0.0s");
        });

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        primaryStage.setTitle("MAS Workbench - Final");
        primaryStage.setScene(scene);
        primaryStage.show();

        startJade();

        new AnimationTimer() {
            @Override
            public void handle(long now) {

                if (CityMap.getInstance().isSimulationRunning()) {

                    currentTime += 0.016;

                    // METRICS & CSV
                    int currentTotalPassed = 0;
                    double totalCO2 = 0;
                    for (TrafficNode n : CityMap.getInstance().getAllIntersections().values()) {
                        currentTotalPassed += n.getTotalPassed();
                        totalCO2 += n.getTotalCO2();
                    }

                    if (currentTime % 1.0 < 0.02) {
                        CityMap.getInstance().logToCSV(currentTime, liveAvgWait, currentTotalPassed, totalCO2);
                    }

                    // TRIGGERS
                    if (!eventsTriggered && currentTime >= maxTime / 2.0) {
                        eventsTriggered = true;
                        if (rbFailCorner.isSelected()) CityMap.getInstance().toggleSensorFailure("Node_0_0");
                        else if (rbFailCenter.isSelected()) CityMap.getInstance().toggleSensorFailure("Node_1_1");
                        else if (rbAmb.isSelected()) {
                            CityMap.getInstance().startAmbulanceTimer();
                            CityMap.getInstance().spawnAmbulance("Node_0_1");
                        }
                    }

                    if (currentTime >= maxTime) {
                        currentTime = maxTime;
                        CityMap.getInstance().setSimulationRunning(false);
                        CityMap.getInstance().stopAmbulanceTimer();
                    }
                }
                lblTimer.setText(String.format("%.1f", currentTime));
                draw(canvas.getGraphicsContext2D());
            }
        }.start();
    }

    // ------------------------------------------------------------
    // RENDERING
    // ------------------------------------------------------------
    private void draw(GraphicsContext gc) {

        double w = WIDTH;
        double h = HEIGHT - 100;

        // Background
        gc.setFill(Color.web("#3E8E41"));
        gc.fillRect(0, 0, w, h);

        // Roads
        gc.setFill(Color.web("#444444"));

        // Horizontal
        for (int i = 0; i < GRID_SIZE; i++) {
            double y = OFFSET + (i * SPACING);
            gc.fillRect(0, y - ROAD_W / 2, w, ROAD_W);

            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.setLineDashes(15);
            gc.strokeLine(0, y, w, y);
        }

        // Vertical
        for (int i = 0; i < GRID_SIZE; i++) {
            double x = OFFSET + (i * SPACING);
            gc.fillRect(x - ROAD_W / 2, 0, ROAD_W, h);
            gc.strokeLine(x, 0, x, h);
        }

        gc.setLineDashes(0);

        // Intersections
        for (TrafficNode node : CityMap.getInstance().getAllIntersections().values())
            drawIntersection(gc, node);

        drawScoreboard(gc, w, h);
    }

    private void drawIntersection(GraphicsContext gc, TrafficNode node) {

        int cx = node.getX();
        int cy = node.getY();

        // Lights
        Color nsColor, ewColor;

        if (node.isInTransition()) {
            nsColor = Color.YELLOW;
            ewColor = Color.YELLOW;
        } else {
            nsColor = node.isNsGreen() ? Color.LIME : Color.RED;
            ewColor = node.isNsGreen() ? Color.RED : Color.LIME;
        }

        gc.setFill(Color.BLACK);
        gc.fillRect(cx - 15, cy - 40, 30, 15);
        gc.setFill(nsColor);
        gc.fillOval(cx - 8, cy - 38, 10, 10);

        gc.setFill(Color.BLACK);
        gc.fillRect(cx - 40, cy + 15, 15, 30);
        gc.setFill(ewColor);
        gc.fillOval(cx - 38, cy + 20, 10, 10);

        // Cars
        int nsCount = node.getRealQueueNS();
        int drawNS = Math.min(nsCount, 10);
        for (int i = 0; i < drawNS; i++)
            drawCar(gc, cx - CAR_SIZE / 2 + 10, cy - 50 - (i * CAR_SPACING), Color.CYAN);

        int totalEW = node.getRealQueueEW();
        int left = totalEW / 2;
        int right = totalEW - left;

        int drawLeft = Math.min(left, 8);
        int drawRight = Math.min(right, 8);

        for (int i = 0; i < drawLeft; i++)
            drawCar(gc, cx - 50 - (i * CAR_SPACING), cy - CAR_SIZE / 2 - 10, Color.ORANGE);
        for (int i = 0; i < drawRight; i++)
            drawCar(gc, cx + 35 + (i * CAR_SPACING), cy - CAR_SIZE / 2 + 10, Color.ORANGE);

        // Ambulance marker
        if (node.hasAmbulance()) {

            gc.setFill(Color.WHITE);
            gc.fillRect(cx - 12, cy - 12, 24, 24);

            gc.setFill(Color.RED);
            gc.fillRect(cx - 10, cy - 4, 20, 8);
            gc.fillRect(cx - 4, cy - 10, 8, 20);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            gc.fillText("+", cx - 10, cy - 15);
        }

        // Info box
        if (!node.areSensorsWorking()) {
            gc.setFill(Color.RED);
            gc.fillRect(cx + 15, cy - 35, 70, 40);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            gc.fillText("SENSOR", cx + 20, cy - 20);
            gc.fillText("ERROR", cx + 20, cy);
        } else {
            gc.setFill(Color.color(0, 0, 0, 0.6));
            gc.fillRect(cx + 15, cy - 35, 60, 40);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            gc.fillText("N: " + node.getQueueNS(), cx + 20, cy - 20);
            gc.fillText("W: " + node.getQueueEW(), cx + 20, cy);

            CityMap.Mode mode = CityMap.getInstance().getMode();
            boolean isAdaptive = (mode == CityMap.Mode.LEARNING_ONLY || mode == CityMap.Mode.SMART);

            if (isAdaptive && node.getThreshold() != 8) {
                gc.setFill(Color.CYAN);
                gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
                gc.fillText("T:" + node.getThreshold(), cx - 35, cy + 10);
            }
        }
    }

    private void drawCar(GraphicsContext gc, double x, double y, Color color) {
        gc.setFill(color);
        gc.fillOval(x, y, CAR_SIZE, CAR_SIZE);
        gc.setFill(color.darker());
        gc.fillOval(x + 3, y + 3, CAR_SIZE - 6, CAR_SIZE - 6);
    }

    // ------------------------------------------------------------
    // SCOREBOARD
    // ------------------------------------------------------------
    private void drawScoreboard(GraphicsContext gc, double w, double h) {
        // 1. GATHER DATA
        int currentTotalPassed = 0;
        long currentTotalWait = 0;
        double totalAvgQ = 0;
        double totalCO2 = 0;
        int count = 0;

        for (TrafficNode n : CityMap.getInstance().getAllIntersections().values()) {
            currentTotalPassed += n.getTotalPassed();
            currentTotalWait += n.getTotalWaitTimeRaw();
            totalAvgQ += n.getAverageQueue();
            totalCO2 += n.getTotalCO2();
            count++;
        }

        // 2. LIVE METRICS (Rolling Window)
        if (currentTime - lastSnapshotTime > 5.0) {
            long deltaWait = currentTotalWait - lastTotalWait;
            int deltaPassed = currentTotalPassed - lastTotalPassed;
            if (deltaPassed > 0) {
                liveAvgWait = (double) deltaWait / deltaPassed;
            } else {
                liveAvgWait = 0.0;
            }
            lastSnapshotTime = currentTime;
            lastTotalWait = currentTotalWait;
            lastTotalPassed = currentTotalPassed;
        }

        // 3. GLOBAL AVERAGES
        double globalAvgWait = (currentTotalPassed > 0) ? (double)currentTotalWait / currentTotalPassed : 0.0;
        double globalAvgQ = (count > 0) ? totalAvgQ / count : 0.0;
        double efficiencyCO2 = (currentTotalPassed > 0) ? totalCO2 / currentTotalPassed : 0.0;

        // 4. DRAW BOARD
        gc.setFill(Color.color(0, 0, 0, 0.85));
        gc.fillRoundRect(w - 320, 10, 310, 200, 10, 10);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));

        String modeName = CityMap.getInstance().getMode().toString();
        int y = 30; int step = 20;
        gc.fillText("MODE: " + modeName, w - 300, y); y+=step;
        gc.fillText("----------------------------", w - 300, y); y+=step;
        gc.fillText(String.format("Throughput : %d", currentTotalPassed), w - 300, y); y+=step;
        gc.fillText(String.format("Avg Queue  : %.2f", globalAvgQ), w - 300, y); y+=step;
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText(String.format("Sess. Wait : %.1fs", globalAvgWait), w - 300, y); y+=step;

        if (liveAvgWait <= globalAvgWait) gc.setFill(Color.LIME);
        else gc.setFill(Color.RED);
        gc.fillText(String.format("LIVE WAIT  : %.1fs", liveAvgWait), w - 300, y); y+=step;

        gc.setFill(Color.ORANGE);
        gc.fillText(String.format("Avg CO2/Car: %.2f", efficiencyCO2), w - 300, y); y+=step;

        double ambTime = CityMap.getInstance().getAmbulanceTotalTime();
        if (ambTime > 0) {
            gc.setFill(Color.RED);
            gc.fillText(String.format("Amb. Trip  : %.1fs", ambTime), w - 300, y);
        }

        if (!CityMap.getInstance().isSimulationRunning() && currentTime >= maxTime) {
            gc.setFill(Color.RED);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 40));
            gc.fillText("FINISHED", w/2 - 100, h/2);
        }
    }

    private void startJade() {
        new Thread(() -> {
            try {
                Runtime rt = Runtime.instance();
                Profile p = new ProfileImpl();
                p.setParameter(Profile.MAIN_HOST, "localhost");
                p.setParameter(Profile.GUI, "false");
                AgentContainer mc = rt.createMainContainer(p);

                int spacing = 200;
                int offset = 150;

                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        String name = "Node_" + row + "_" + col;
                        int x = offset + (col * spacing);
                        int y = offset + (row * spacing);
                        java.util.List<String> neighbors = new java.util.ArrayList<>();
                        if (row > 0) neighbors.add("Node_" + (row - 1) + "_" + col);
                        if (row < 2) neighbors.add("Node_" + (row + 1) + "_" + col);
                        if (col > 0) neighbors.add("Node_" + row + "_" + (col - 1));
                        if (col < 2) neighbors.add("Node_" + row + "_" + (col + 1));

                        Object[] agentArgs = new Object[2 + neighbors.size()];
                        agentArgs[0] = x;
                        agentArgs[1] = y;
                        for (int i = 0; i < neighbors.size(); i++)
                            agentArgs[i + 2] = neighbors.get(i);

                        AgentController ac = mc.createNewAgent(name, "agents.IntersectionAgent", agentArgs);
                        ac.start();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}