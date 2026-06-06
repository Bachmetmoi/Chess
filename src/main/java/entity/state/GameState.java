package entity.state;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import entity.board.ChessBoard;
import entity.enums.Faction;
import entity.enums.Result;
import entity.move.Move;

public class GameState {
    // attributes
    private Faction turn;
    private List<Move> moveHistory;
    private Result gameStatus;
    private ChessBoard chessBoard;
    // how many times each position (placement + turn + castling + en passant)
    // has occurred, used to detect threefold repetition
    private Map<String, Integer> positionHistory = new HashMap<>();

    // constructor
    public GameState(Faction t, List<Move> m, Result g, ChessBoard c) {
        turn = t;
        moveHistory = m;
        gameStatus = g;
        chessBoard = c;
    }

    // methods
    public Faction getTurn() {
        return turn;
    }

    public List<Move> getMoveHistory() {
        return moveHistory;
    }

    public Result getGameStatus() {
        return gameStatus;
    }

    public ChessBoard getChessBoard() {
        return chessBoard;
    }

    public void setChessBoard(ChessBoard c) {
        chessBoard = c;
    }

    public void setTurn(Faction t) {
        turn = t;
    }

    public void setGameStatus(Result r) {
        gameStatus = r;
    }

    public void addMoveHistory(Move m) {
        moveHistory.add(m);
    }

    /**
     * Records that the given position has been reached once more and returns how
     * many times it has now occurred. A return value of 3 (or more) means the
     * threefold repetition rule applies.
     */
    public int recordPosition(String positionKey) {
        int count = positionHistory.getOrDefault(positionKey, 0) + 1;
        positionHistory.put(positionKey, count);
        return count;
    }

    /**
     * Removes one occurrence of the given position, undoing a previous
     * {@link #recordPosition} call. Keeps the threefold repetition counts in sync
     * when a move is taken back, so an undone repetition no longer counts.
     */
    public void unrecordPosition(String positionKey) {
        Integer count = positionHistory.get(positionKey);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            positionHistory.remove(positionKey);
        } else {
            positionHistory.put(positionKey, count - 1);
        }
    }
}
