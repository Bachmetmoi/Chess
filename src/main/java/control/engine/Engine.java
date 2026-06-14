package control.engine;

import entity.move.Move;
import entity.state.GameState;

public interface Engine {
    // methods
    public Move chooseMove(GameState gamestate);
}
