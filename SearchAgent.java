import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;
import edu.cwru.sepia.util.DistanceMetrics;
import edu.cwru.sepia.util.Pair;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by Mike Xu on 2/2/14.
 * This agent performs pathfinding using the A* algorithm
 */
public class SearchAgent extends Agent {
    private static final long serialVersionUID = -4047208702628325380L;

    State.StateView currentState;

    int footmanId;
    int townHallId;
    LinkedList<Pair<Integer, Integer>> path;
    List<Pair<Integer, Integer>> obstacles;

    public SearchAgent(int playernum, String[] args) {
        super(playernum);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newState, History.HistoryView stateHistory) {
        currentState = newState;
        // identify the footman and town hall
        for(int id: currentState.getAllUnitIds()) {
            String unitType = currentState.getUnit(id).getTemplateView().getName();
            if(unitType.equals("Footman")) footmanId = id;
            if(unitType.equals("TownHall")) townHallId = id;
        }

        // identify obstacles (trees)
        obstacles = new ArrayList<Pair<Integer, Integer>>();
        for(ResourceNode.ResourceView res: currentState.getResourceNodes(ResourceNode.Type.TREE))
            obstacles.add(new Pair<Integer, Integer>(res.getXPosition(), res.getYPosition()));

        // calculate path from footman to town hall
        Unit.UnitView footman = currentState.getUnit(footmanId);
        Unit.UnitView townHall = currentState.getUnit(townHallId);
        path = findPath(new Pair<Integer, Integer>(footman.getXPosition(), footman.getYPosition()),
                        new Pair<Integer, Integer>(townHall.getXPosition(), townHall.getYPosition()));
        // exit if no valid path can be found
        if(path == null) {
            System.out.println("No valid path");
            System.exit(0);
        }

        // remove the first step since the footman is already there
        path.removeFirst();

        return middleStep(newState, stateHistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newState, History.HistoryView stateHistory) {
        currentState = newState;

        Map<Integer, Action> actions = new HashMap<Integer, Action>();
        Unit.UnitView footman = currentState.getUnit(footmanId);
        if(path.size() > 0) {
            // move the footman along the path
            Pair<Integer, Integer> A = new Pair<Integer, Integer>(footman.getXPosition(),  footman.getYPosition());
            Pair<Integer, Integer> B = path.pop();
            actions.put(footmanId, Action.createPrimitiveMove(footmanId, Direction.getDirection(B.a - A.a, B.b - A.b)));
        } else {
            // attack the town hall once the footman has reached it
            actions.put(footmanId, Action.createCompoundAttack(footmanId, townHallId));
        }

        return actions;
    }

    @Override
    public void terminalStep(State.StateView newState, History.HistoryView stateHistory) {}

    @Override
    public void savePlayerData(OutputStream outputStream) {}

    @Override
    public void loadPlayerData(InputStream inputStream) {}

    // A* pathfinding algorithm
    private LinkedList<Pair<Integer, Integer>> findPath(Pair<Integer, Integer> start, Pair<Integer, Integer> end) {
        List<Pair<Integer, Integer>> closed = new ArrayList<Pair<Integer, Integer>>();
        List<Pair<Integer, Integer>> open = new ArrayList<Pair<Integer, Integer>>();
        Map<Pair<Integer, Integer>, Integer> gScore = new HashMap<Pair<Integer, Integer>, Integer>();
        Map<Pair<Integer, Integer>, Integer> fScore = new HashMap<Pair<Integer, Integer>, Integer>();
        Map<Pair<Integer, Integer>, Pair<Integer, Integer>> parents = new HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>>();

        // initialize starting location
        open.add(start);
        gScore.put(start, 0);
        fScore.put(start, gScore.get(start) + DistanceMetrics.chebyshevDistance(start.a, start.b, end.a, end.b));

        // expand possible moves
        while(!open.isEmpty()) {
            // get the least cost open location
            Pair<Integer, Integer> current = getMinVal(fScore, open);
            // return the least cost path if the end has been reached
            if(current.a.equals(end.a) && current.b.equals(end.b)) return buildPath(parents, end);
            // move expanded position to the closed list
            open.remove(current);
            closed.add(current);
            // evaluate next possible moves from current location
            for(Pair<Integer, Integer> neighbor: getNeighbors(current)) {
                // ignore locations in the closed set
                if(closed.contains(neighbor)) continue;
                // set gScore to 14 for diagonal neighbors and 10 for adjacent neighbors
                int tempScore = gScore.get(current) + ((!neighbor.a.equals(current.a) && !neighbor.b.equals(current.b)) ? 14 : 10);
                // explore low cost paths
                if(!open.contains(neighbor) || tempScore <= gScore.get(neighbor)) {
                    // track the path
                    parents.put(neighbor, current);
                    gScore.put(neighbor, tempScore);
                    // calculate heuristic cost
                    fScore.put(neighbor, gScore.get(neighbor) + DistanceMetrics.chebyshevDistance(neighbor.a, neighbor.b, end.a, end.b));
                    if(!open.contains(neighbor)) open.add(neighbor);
                }
            }
        }
        return null;
    }

    // return the list item mapped to the lowest score
    private Pair<Integer, Integer> getMinVal(Map<Pair<Integer, Integer>, Integer> score, List<Pair<Integer, Integer>> list) {
        Pair<Integer, Integer> result = null;
        int minScore = Integer.MAX_VALUE;
        // iterate over all locations and costs
        for(Pair<Integer, Integer> key: score.keySet()) {
            // update minimum score and result
            if(score.get(key) < minScore && list.contains(key)) {
                minScore = score.get(key);
                result = key;
            }
        }
        return result;
    }

    // get valid moves from a given location
    private List<Pair<Integer, Integer>> getNeighbors(Pair<Integer, Integer> A) {
        List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer, Integer>>();
        // check within x +/- 1 and y +/- 1 of given location
        for(int tx = -1; tx < 2; tx++) {
            for(int ty = -1; ty < 2; ty++) {
                Pair<Integer, Integer> B = new Pair<Integer, Integer>(A.a + tx, A.b + ty);
                if(B.a >= 0 && B.a < currentState.getXExtent() && B.b >= 0 && B.b < currentState.getYExtent() && !obstacles.contains(B))
                    // add the location if it is within the bounds and does not contain an obstacle
                    result.add(B);
            }
        }
        return result;
    }

    // extract shortest path from a given point to the root node by tracing the parents
    private LinkedList<Pair<Integer, Integer>> buildPath(Map<Pair<Integer, Integer>, Pair<Integer, Integer>> parents, Pair<Integer, Integer> A) {
        LinkedList<Pair<Integer, Integer>> result = new LinkedList<Pair<Integer, Integer>>();
        // iterate over the parents and store them in reverse order
        if(parents.containsKey(A)) result = buildPath(parents, parents.get(A));
        result.add(A);
        return result;
    }
}
