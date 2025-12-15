package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import simulation.CityMap;
import simulation.TrafficNode;


public class IntersectionAgent extends Agent {

    private TrafficNode myIntersection;
    private AID southNeighbor = null;
    private AID eastNeighbor = null;
    private int myQueueNS, myQueueEW;
    private boolean isGreenNS;
    private boolean greenWaveIncoming = false;
    private double maxNeighborPheromone = 0.0;
    private boolean sensorsBroken = false;
    private boolean ambulanceApproaching = false;
    private int greenLightThreshold = 10;
    private int avgQueueHistory = 0;
    private int learningTicks = 0;
    private int minGreenTime = 0;
    private int fixedCycleTimer = 0;

    private RLBrain rlBrain;
    private MicroStateEncoder rlEncoder;
    private int previousState = -1;
    private int previousAction = -1;
    private int rlActionTimer = 0;

    @Override
    protected void setup() {
        rlBrain = new RLBrain();
        rlEncoder = new MicroStateEncoder();

        // Agent discovers itself and it's neighbours
        Object[] args = getArguments();
        int x = 0, y = 0;
        if (args != null && args.length >= 2) {
            try { x = Integer.parseInt(args[0].toString()); y = Integer.parseInt(args[1].toString()); } catch (Exception e) {}
            for (int i = 2; i < args.length; i++) detectNeighbor((String) args[i]);
        }

        myIntersection = new TrafficNode(getLocalName(), x, y);
        CityMap.getInstance().addIntersection(getLocalName(), myIntersection);


        addBehaviour(new TickerBehaviour(this, 1000) {

            boolean wasRunning = false;

            @Override
            protected void onTick() {
                try {
                    boolean isRunning = CityMap.getInstance().isSimulationRunning();

                    if (wasRunning && !isRunning) {
                        if (CityMap.getInstance().getMode() == CityMap.Mode.SMART) {

                            System.out.println("Printing Q-Table for agent: " + getLocalName());
                            rlBrain.printQTable();
                        }
                    }

                    wasRunning = isRunning;

                    if (myIntersection.checkAndClearReset()) resetBeliefs();
                    if (!isRunning) return;

                    if (minGreenTime > 0) minGreenTime--;
                    if (fixedCycleTimer > 0) fixedCycleTimer--;
                    if (rlActionTimer > 0) rlActionTimer--;

                    myIntersection.updatePheromones();
                    if (CityMap.getInstance().getMode() == CityMap.Mode.LEARNING_ONLY) adaptStrategy();
                    updateBeliefsAndPhysics();

                    CityMap.Mode mode = CityMap.getInstance().getMode();

                    if (mode == CityMap.Mode.SMART) {
                        runRLLogic();
                    } else {
                        Desire desire = deliberate(mode);
                        Intention intention = plan(desire);
                        execute(intention);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && msg.getPerformative() == ACLMessage.PROPAGATE) greenWaveIncoming = true;
                else block();
            }
        });
    }

    private void detectNeighbor(String neighborName) {
        try {
            String[] myParts = getLocalName().split("_");
            String[] nParts = neighborName.split("_");
            int myR = Integer.parseInt(myParts[1]);
            int myC = Integer.parseInt(myParts[2]);
            int nR = Integer.parseInt(nParts[1]);
            int nC = Integer.parseInt(nParts[2]);
            if (nC == myC && nR == myR + 1) southNeighbor = new AID(neighborName, AID.ISLOCALNAME);
            if (nR == myR && nC == myC + 1) eastNeighbor = new AID(neighborName, AID.ISLOCALNAME);
        } catch (Exception e) {}
    }

    private void updateBeliefsAndPhysics() {
        // Traffic generation
        String[] parts = getLocalName().split("_");
        int myRow = Integer.parseInt(parts[1]);

        if (myRow == 0) {
            myIntersection.generateTrafficFlow();
        } else {
            if (Math.random() < 0.05) {
                myIntersection.addCarsEastWest(1);
            }
        }

        // Physics
        boolean hadAmbulance = myIntersection.hasAmbulance();
        int carsPassed = myIntersection.processFlow();
        boolean hasAmbulanceNow = myIntersection.hasAmbulance();

        if (hadAmbulance && !hasAmbulanceNow) {
            if (southNeighbor != null) CityMap.getInstance().spawnAmbulance(southNeighbor.getLocalName());
            else CityMap.getInstance().stopAmbulanceTimer();
        }

        if (carsPassed > 0) {
            if (myIntersection.isNsGreen()) {
                if (southNeighbor != null) sendCarsTo(southNeighbor, carsPassed, true);
            } else {
                if (eastNeighbor != null) sendCarsTo(eastNeighbor, carsPassed, false);
            }
        }

        //Stigmergy
        maxNeighborPheromone = 0.0;
        if (eastNeighbor != null) {
            TrafficNode n = CityMap.getInstance().getIntersection(eastNeighbor.getLocalName());
            if (n != null) maxNeighborPheromone = Math.max(maxNeighborPheromone, n.getPheromoneLevel());
        }

        // Get world information
        myQueueNS = myIntersection.getQueueNS();
        myQueueEW = myIntersection.getQueueEW();
        isGreenNS = myIntersection.isNsGreen();
        sensorsBroken = (myQueueNS == -1 || myQueueEW == -1);
        ambulanceApproaching = myIntersection.hasAmbulance();
    }

    private void sendCarsTo(AID target, int amount, boolean isSouthBound) {
        TrafficNode node = CityMap.getInstance().getIntersection(target.getLocalName());
        boolean success = false;

        if (node != null) {
            if (isSouthBound) success = node.addCarsNorthSouth(amount);
            else success = node.addCarsEastWest(amount);
        } else {
            success = true; //  Car exiting node
        }

        if (success) {
            CityMap.Mode mode = CityMap.getInstance().getMode();
            if (mode == CityMap.Mode.COORD_GREEN_WAVE) {
                ACLMessage msg = new ACLMessage(ACLMessage.PROPAGATE);
                msg.addReceiver(target);
                msg.setContent("INCOMING_CARS");
                send(msg);
            }
        } else {
            // Neighbour is full
            // Put cars back
            if(isSouthBound) myIntersection.addCarsNorthSouth(amount);
            else myIntersection.addCarsEastWest(amount);
        }
    }


    private void runRLLogic() {
        if (ambulanceApproaching) {
            rlActionTimer = 0;
            if (isGreenNS) execute(Intention.KEEP_CURRENT_PHASE);
            else execute(Intention.SWITCH_PHASE);
            return;
        }

        if (rlActionTimer > 0) {
            execute(Intention.KEEP_CURRENT_PHASE);
            return;
        }

        int currentState = rlEncoder.encodeState(myQueueNS, myQueueEW, isGreenNS);

        if (previousState != -1) {
            boolean didSwitch = (previousAction == RLBrain.ACTION_SWITCH);
            double reward = rlEncoder.calculateReward(myQueueNS, myQueueEW, didSwitch);
            rlBrain.update(previousState, previousAction, reward, currentState);
        }

        int action = rlBrain.chooseAction(currentState);

        // Safety Masking
        if (!isGreenNS && action == RLBrain.ACTION_HOLD_LONG) {
            action = RLBrain.ACTION_SWITCH;
        }
        if (isGreenNS && myQueueNS > 30 && action == RLBrain.ACTION_SWITCH) {
            action = RLBrain.ACTION_HOLD_LONG;
        }

        Intention intention;

        if (action == RLBrain.ACTION_SWITCH) {
            intention = Intention.SWITCH_PHASE;
            rlActionTimer = 0;
        } else if (action == RLBrain.ACTION_HOLD_SHORT) {
            intention = Intention.KEEP_CURRENT_PHASE;
            rlActionTimer = 5;
        } else {
            intention = Intention.KEEP_CURRENT_PHASE;
            rlActionTimer = 20;
        }

        if (myIntersection.isInTransition())
            intention = Intention.KEEP_CURRENT_PHASE;
        execute(intention);

        previousState = currentState;
        previousAction = action;
    }

    private Desire deliberate(CityMap.Mode mode) {
        if (ambulanceApproaching) return Desire.PASS_EMERGENCY;

        if (mode == CityMap.Mode.BASELINE_FIXED) return Desire.FIXED_CYCLE;

        if (sensorsBroken) return Desire.FAIL_SAFE_MODE;

        boolean useGW = (mode == CityMap.Mode.COORD_GREEN_WAVE);
        boolean useStig = (mode == CityMap.Mode.COORD_STIGMERGY);
        int myMaxQueue = Math.max(myQueueNS, myQueueEW);

        if (useStig && maxNeighborPheromone > 8.0 && myMaxQueue < 4) return Desire.PREVENT_GRIDLOCK;
        if (useGW && greenWaveIncoming && myMaxQueue < 15) return Desire.PREPARE_GREEN_WAVE;

        return Desire.MANAGE_LOCAL_TRAFFIC;
    }

    private Intention plan(Desire desire) {
        if (desire == Desire.PASS_EMERGENCY) {
            // Ambulance is always NS.
            // If Green is NS, KEEP IT. If Green is EW, switch.
            if (isGreenNS) return Intention.KEEP_CURRENT_PHASE;
            else return Intention.SWITCH_PHASE;
        }

        if (desire == Desire.FIXED_CYCLE || desire == Desire.FAIL_SAFE_MODE) {
            if (fixedCycleTimer <= 0) return Intention.SWITCH_PHASE;
            return Intention.KEEP_CURRENT_PHASE;
        }

        //It's yellow
        if (myIntersection.isInTransition()) return Intention.KEEP_CURRENT_PHASE;

        if (minGreenTime > 0) return Intention.KEEP_CURRENT_PHASE;

        // 3. Gridlock / Green Wave
        if (desire == Desire.PREVENT_GRIDLOCK) return isGreenNS ? Intention.KEEP_CURRENT_PHASE : Intention.SWITCH_PHASE;

        if (desire == Desire.PREPARE_GREEN_WAVE) {
            if (isGreenNS) {
                greenWaveIncoming = false;
                return Intention.SWITCH_PHASE;
            }
            else {
                greenLightThreshold += 20;
                greenWaveIncoming = false;
            }
        }

        boolean switchNeeded = false;
        int effectiveThreshold = greenLightThreshold;
        if (isGreenNS) {
            if (myQueueEW > effectiveThreshold) switchNeeded = true;
            if (myQueueNS == 0 && myQueueEW > 0) switchNeeded = true;
        } else {
            if (myQueueNS > effectiveThreshold) switchNeeded = true;
            if (myQueueEW == 0 && myQueueNS > 0) switchNeeded = true;
        }
        return switchNeeded ? Intention.SWITCH_PHASE : Intention.KEEP_CURRENT_PHASE;
    }

    private void execute(Intention intention) {
        if (intention == Intention.SWITCH_PHASE) {
            myIntersection.switchLight();
            minGreenTime = 4;
            fixedCycleTimer = 15;
        }
    }

    private void adaptStrategy() {
        if (sensorsBroken) return;
        int totalQ = myQueueNS + myQueueEW;
        avgQueueHistory += totalQ;
        learningTicks++;
        if (learningTicks >= 10) {
            double avg = avgQueueHistory / 10.0;
            if (avg > 12) greenLightThreshold += 2;
            else if (avg > 8) greenLightThreshold += 1;
            else if (avg < 4) greenLightThreshold -= 2;
            else greenLightThreshold -= 1;
            greenLightThreshold = Math.max(5, Math.min(20, greenLightThreshold));
            myIntersection.setThreshold(greenLightThreshold);
            avgQueueHistory = 0;
            learningTicks = 0;
        }
    }

    private void resetBeliefs() {
        greenLightThreshold = 10;
        myIntersection.setThreshold(10);
        minGreenTime = 0;
        fixedCycleTimer = 0;
        avgQueueHistory = 0;
        learningTicks = 0;
        greenWaveIncoming = false;
        maxNeighborPheromone = 0.0;
        sensorsBroken = false;
        ambulanceApproaching = false;
        previousState = -1;
        previousAction = -1;
        rlActionTimer = 0;
        System.out.println(getLocalName() + " reset.");
    }
}