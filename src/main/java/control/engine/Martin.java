package control.engine;

import java.util.List;
import java.util.Random;

import control.GameController;
import entity.move.Move;
import entity.state.GameState;

public class Martin implements Engine {
    // attributes
    private GameController gameController;

    // constructor
    public Martin(GameController gameController) {
        this.gameController = gameController;
    }

    // methods
    @Override
    public Move chooseMove(GameState gamestate) {

        List<Move> moves = gameController.getMoves(gamestate);
        if (moves.size() == 0) {
            return null;
        }

        Random random = new Random();
        int temp = random.nextInt(moves.size());
        return moves.get(temp);

    }
}
