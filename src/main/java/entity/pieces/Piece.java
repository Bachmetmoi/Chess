package entity.pieces;

import java.util.List;

import entity.board.Cell;
import entity.enums.Faction;
import entity.move.Move;

public abstract class Piece {
    // attributes
    private int value;
    private Faction side;
    private int moveCount = 0;

    // constructor
    public Piece(int value, Faction side) {
        this.value = value;
        this.side = side;
    }

    // methods

    // overload
    public abstract List<Move> move(Cell[][] board, int curX, int curY);

    public List<Move> move(Cell[][] board, int curX, int curY, List<Move> moveHistory) {
        return move(board, curX, curY);
    }

    public Faction getSide() {
        return side;
    }

    public int getValue() {
        return value;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public void addMoveCount() {
        moveCount += 1;
    }

    public void reduceMoveCount() {
        moveCount -= 1;
    }
}
