import edu.cwru.sepia.action.*;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.BirthLog;
import edu.cwru.sepia.environment.model.history.DeathLog;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.history.ResourceNodeExhaustionLog;
import edu.cwru.sepia.environment.model.state.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by Mike Xu on 2/2/14.
 * This agent will perform the following tasks:
 *  1. build 2 peasants
 *  2. build a farm
 *  3. build a barracks
 *  4. build 2 footmen
 *  5. defeat the enemy footman
 */
public class RCAgent extends Agent {
    private static final long serialVersionUID = -4047208702628325380L;

    private int lastStepMade;
    private int targetGold;
    private int targetWood;
    private Template.TemplateView targetUnit;
    private Map<Integer, Action> unitActions;

    State.StateView currentState;

    public RCAgent(int playernum, String[] args) {
        super(playernum);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newState, History.HistoryView stateHistory) {
        currentState = newState;

        // initialize the agent attributes
        setTarget("Peasant");
        // initialize player units into unitActions map
        unitActions = new HashMap<Integer, Action>();
        for(Integer id: newState.getUnitIds(playernum)) unitActions.put(id, null);

        lastStepMade = newState.getTurnNumber();
        return getActions();
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newState, History.HistoryView stateHistory) {
        currentState = newState;

        List<Integer> allUnitIds = currentState.getAllUnitIds();
        List<Integer> townHallIds = new ArrayList<Integer>();
        List<Integer> peasantIds = new ArrayList<Integer>();
        List<Integer> idlePeasantIds = new ArrayList<Integer>();
        List<Integer> farmIds = new ArrayList<Integer>();
        List<Integer> barracksIds = new ArrayList<Integer>();
        List<Integer> footmanIds = new ArrayList<Integer>();
        List<Integer> enemyIds = new ArrayList<Integer>();

        int currentGold = currentState.getResourceAmount(playernum, ResourceType.GOLD);
        int currentWood = currentState.getResourceAmount(playernum, ResourceType.WOOD);
        int incomingGold = 0;
        int incomingWood = 0;

        // sort all units
        for(int id: allUnitIds) {
            Unit.UnitView uv = currentState.getUnit(id);
            String uType = uv.getTemplateView().getName();
            int uPlayer = uv.getTemplateView().getPlayer();
            if(uPlayer == playernum) {
                // agent's units
                if(uType.equals("TownHall")) townHallIds.add(id);
                if(uType.equals("Peasant")) peasantIds.add(id);
                if(uType.equals("Farm")) farmIds.add(id);
                if(uType.equals("Barracks")) barracksIds.add(id);
                if(uType.equals("Footman")) footmanIds.add(id);
            } else {
                // enemy units
                enemyIds.add(id);
            }
        }

        // read log for all steps taken since agent's last turn and update unitActions
        for(int stepToRead = lastStepMade; stepToRead < currentState.getTurnNumber(); stepToRead++) {
            List<Integer> removeUnits = new LinkedList<Integer>();
            List<Integer> stopUnits = new LinkedList<Integer>();

            for(BirthLog birth: stateHistory.getBirthLogs(stepToRead))
                // add new units to unitActions
                if(birth.getController() == playernum) unitActions.put(birth.getNewUnitID(), null);
            for(DeathLog death: stateHistory.getDeathLogs(stepToRead)) {
                // find dead agent units
                if(death.getController() == playernum) removeUnits.add(death.getDeadUnitID());
                // find agent units attacking dead units
                for(Map.Entry<Integer, Action> action: unitActions.entrySet()) {
                    if(action.getValue() != null) {
                        Action a = Action.createCompoundAttack(action.getKey(), death.getDeadUnitID());
                        if(action.getValue().equals(a)) stopUnits.add(action.getKey());
                    }
                }
            }
            for(ResourceNodeExhaustionLog exhaustion: stateHistory.getResourceNodeExhaustionLogs(stepToRead)) {
                // find peasants gathering exhausted resources
                for(Map.Entry<Integer, Action> action: unitActions.entrySet()) {
                    if(action.getValue() != null) {
                        Action a = Action.createCompoundGather(action.getKey(), exhaustion.getExhaustedNodeID());
                        if(action.getValue().equals(a)) stopUnits.add(action.getKey());
                    }
                }
            }

            for(Integer i: stopUnits) unitActions.put(i, null);
            for(Integer i: removeUnits) unitActions.remove(i);

            for(ActionResult feedback: stateHistory.getCommandFeedback(playernum, stepToRead).values()) {
                if(feedback.getFeedback() != ActionFeedback.INCOMPLETE) {
                    // clear matching completed or failed actions from unitActions
                    Action a = feedback.getAction();
                    int uid = a.getUnitId();
                    Action unitAction = unitActions.get(uid);
                    if(a.equals(unitAction)) {
                        unitActions.put(uid, null);
                    }
                }
            }
        }

        // calculate total incoming resources
        for(Integer peasantId: peasantIds) {
            // calculate resources currently being mined
            Action a = unitActions.get(peasantId);
            if(a != null && a.getType() == ActionType.COMPOUNDGATHER) {
                TargetedAction ta = (TargetedAction) unitActions.get(peasantId);
                ResourceNode.ResourceView res = currentState.getResourceNode(ta.getTargetId());
                // TODO: figure out a way to programatically determine gold/wood capacity
                if(res.getType() == ResourceNode.Type.GOLD_MINE) incomingGold += 100;
                else if(res.getType() == ResourceNode.Type.TREE) incomingWood += 100;
            // calculate resources currently being carried
            } else {
                Unit.UnitView peasant = currentState.getUnit(peasantId);
                ResourceType type = peasant.getCargoType();
                if(type == ResourceType.GOLD) incomingGold += peasant.getCargoAmount();
                else if(type == ResourceType.WOOD) incomingWood += peasant.getCargoAmount();
            }
        }

        // gather resources to reach the target amounts
        for(Integer peasantId: peasantIds) {
            Unit.UnitView peasant = currentState.getUnit(peasantId);
            // command idle peasants
            if(unitActions.get(peasantId) == null) {
                if(peasant.getCargoAmount() > 0) {
                    // return cargo to town hall
                    unitActions.put(peasantId, Action.createCompoundDeposit(peasantId, townHallIds.get(0)));
                } else {
                    // gather wood and gold as needed
                    List<Integer> goldIds = currentState.getResourceNodeIds(ResourceNode.Type.GOLD_MINE);
                    List<Integer> woodIds = currentState.getResourceNodeIds(ResourceNode.Type.TREE);
                    if(currentWood + incomingWood < targetWood && woodIds.size() > 0) {
                        unitActions.put(peasantId, Action.createCompoundGather(peasantId, woodIds.get(0)));
                    } else if(currentGold + incomingGold < targetGold && goldIds.size() > 0) {
                        unitActions.put(peasantId, Action.createCompoundGather(peasantId, goldIds.get(0)));
                    } else {
                        idlePeasantIds.add(peasantId);
                    }
                }
            }
        }

        // build order
        String targetName = targetUnit != null ? targetUnit.getName() : "";
        if(targetName.equals("Peasant")) { // build 2 peasants
            if(currentGold >= targetUnit.getGoldCost() && currentWood >= targetUnit.getWoodCost()) {
                int townHallId = townHallIds.get(0);
                unitActions.put(townHallId, Action.createCompoundProduction(townHallId, targetUnit.getID()));
            }
            if(peasantIds.size() > 2) setTarget("Farm");
        } else if(targetName.equals("Farm")) { // build a farm
            if(currentGold >= targetUnit.getGoldCost() && currentWood >= targetUnit.getWoodCost() && idlePeasantIds.size() > 0) {
                int peasantId = idlePeasantIds.get(0);
                Unit.UnitView townHall = currentState.getUnit(townHallIds.get(0));
                unitActions.put(peasantId, Action.createCompoundBuild(peasantId, targetUnit.getID(), townHall.getXPosition() + 3, townHall.getYPosition()));
            }
            if(farmIds.size() > 0) setTarget("Barracks");
        } else if(targetName.equals("Barracks")) { // build a barracks
            if(currentGold >= targetUnit.getGoldCost() && currentWood >= targetUnit.getWoodCost() && idlePeasantIds.size() > 0) {
                int peasantId = idlePeasantIds.get(0);
                Unit.UnitView townHall = currentState.getUnit(townHallIds.get(0));
                unitActions.put(peasantId, Action.createCompoundBuild(peasantId, targetUnit.getID(), townHall.getXPosition() - 3, townHall.getYPosition()));
            }
            if(barracksIds.size() > 0) setTarget("Footman");
        } else if(targetName.equals("Footman")) { // build 2 footmen
            if(currentGold >= targetUnit.getGoldCost() && currentWood >= targetUnit.getWoodCost()) {
                int barracksId = barracksIds.get(0);
                unitActions.put(barracksId, Action.createCompoundProduction(barracksId, targetUnit.getID()));
            }
            if(footmanIds.size() > 1) {
                setTarget("");
            }
        }

        // order all footmen to attack the next available enemy
        if(footmanIds.size() > 0 && enemyIds.size() > 0) {
            for(int footmanId: footmanIds) {
                if(unitActions.get(footmanId) == null) unitActions.put(footmanId, Action.createCompoundAttack(footmanId, enemyIds.get(0)));
            }
        }

        lastStepMade = currentState.getTurnNumber();
        return getActions();
    }

    @Override
    public void terminalStep(State.StateView newState, History.HistoryView stateHistory) {}

    @Override
    public void savePlayerData(OutputStream outputStream) {}

    @Override
    public void loadPlayerData(InputStream inputStream) {}

    // returns an action map without null values
    private Map<Integer, Action> getActions() {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();
        for(Map.Entry<Integer, Action> action: unitActions.entrySet())
            if(action.getValue() != null) actions.put(action.getKey(), action.getValue());
        return actions;
    }

    // set target gold and wood based on unit template
    private void setTarget(String templateName) {
        Template.TemplateView t = currentState.getTemplate(playernum, templateName);
        targetUnit = t;
        targetGold = t != null ? t.getGoldCost() : 0;
        targetWood = t != null ? t.getWoodCost() : 0;
    }
}
