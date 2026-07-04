package control; // sits with the engine: it is part of the "how the engine picks a move" logic

import java.util.ArrayList; // collects the playable book candidates before picking one
import java.util.List; // the List interface type we pass around
import java.util.Random; // picks one of the candidate first moves at random

import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.move.Move; // the abstract "a move" type (NormalMove, Castling, Promotion, ...)
import entity.state.GameState; // holds whose turn it is, the board, and the move history

/**
 * A tiny hardcoded "opening book": when the position matches a known opening we
 * return a prepared move instead of searching. Coordinates are board[x][y] with
 * x = file (a=0 .. h=7) and y = rank (rank 1 = 0 .. rank 8 = 7).
 *
 * As White the book chooses the FIRST move at random from a fixed set of sound
 * openings ({@link #WHITE_FIRST_MOVES}: 1.e4, 1.d4, 1.Nf3, 1.Nc3), so the engine
 * does not open every game identically.
 *
 * As Black it follows one short line, matched against the exact move history:
 *   1.e4 (e2-e4)            -&gt; 1...e5  (e7-e5)
 *   1.e4 e5 2.Nf3 (g1-f3)   -&gt; 2...Nc6 (b8-c6)
 * Each rule fires only when every preceding move matches, so the engine never
 * blunders into a book reply that does not fit the position actually on the board.
 *
 * To extend the book: add rows to {@link #WHITE_FIRST_MOVES} for more first-move
 * choices, or add further history-matched rules in {@link #blackBookMove} /
 * {@link #whiteBookMove} for deeper lines.
 */
public class OpeningBook {

    // White's first-move repertoire, one row per candidate as {startX, startY,
    // endX, endY}. Extending the repertoire is just adding a row here.
    private static final int[][] WHITE_FIRST_MOVES = {
            { 4, 1, 4, 3 }, // 1.e4  (e2-e4)
            { 3, 1, 3, 3 }, // 1.d4  (d2-d4)
            { 6, 0, 5, 2 }, // 1.Nf3 (g1-f3)
            { 1, 0, 2, 2 }, // 1.Nc3 (b1-c3)
    };

    private final Random random = new Random(); // varies the chosen first move from game to game

    /**
     * The public entry point: returns the book move for the side to move in this
     * position, or {@code null} if no rule applies (the engine then falls back to
     * its normal search).
     *
     * @param state      the current game state (turn, board, move history)
     * @param legalMoves every legal move for the side to move, already generated
     */
    public Move bookMove(GameState state, List<Move> legalMoves) {
        if (state.getTurn() == Faction.WHITE) {
            return whiteBookMove(state, legalMoves);
        }
        return blackBookMove(state, legalMoves);
    }

    /** White's side of the book: currently only the randomised first move. */
    private Move whiteBookMove(GameState state, List<Move> legalMoves) {
        if (state.getMoveHistory().isEmpty()) { // the game's very first move
            return randomFirstMove(legalMoves);
        }
        return null; // no White rule beyond move one yet: fall back to the search
    }

    /** Black's side of the book: prepared replies matched against the history. */
    private Move blackBookMove(GameState state, List<Move> legalMoves) {
        List<Move> history = state.getMoveHistory(); // the moves played so far

        if (history.size() == 1) { // White has made only its first move
            if (isMove(history.get(0), 4, 1, 4, 3)) { // 1.e4 (e2-e4)
                return findLegalMove(legalMoves, 4, 6, 4, 4); // reply 1...e5 (e7-e5)
            }
            return null;
        }

        if (history.size() == 3) { // we are answering White's second move
            if (isMove(history.get(0), 4, 1, 4, 3) // 1.e4
                    && isMove(history.get(1), 4, 6, 4, 4) // 1...e5
                    && isMove(history.get(2), 6, 0, 5, 2)) { // 2.Nf3 (g1-f3)
                return findLegalMove(legalMoves, 1, 7, 2, 5); // reply 2...Nc6 (b8-c6)
            }
            return null;
        }

        return null; // no book rule covers this position: fall back to the search
    }

    /**
     * Picks one of {@link #WHITE_FIRST_MOVES} at random. Only candidates that are
     * actually in {@code legalMoves} are considered (all four always are from the
     * standard start position, but a customized setup may rule some out), so the
     * book can never return an illegal move.
     */
    private Move randomFirstMove(List<Move> legalMoves) {
        List<Move> candidates = new ArrayList<>(); // the book moves playable here
        for (int[] m : WHITE_FIRST_MOVES) {
            Move move = findLegalMove(legalMoves, m[0], m[1], m[2], m[3]);
            if (move != null) { // legal in this position: keep it as a candidate
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) { // customized position where none apply
            return null; // no book move: fall back to the search
        }
        return candidates.get(random.nextInt(candidates.size())); // pick one at random
    }

    /**
     * True if {@code move} goes from (startX, startY) to (endX, endY). Used to match
     * a played move (or a candidate reply) against the opening book's coordinates.
     */
    private boolean isMove(Move move, int startX, int startY, int endX, int endY) {
        return move.getStartXPos() == startX && move.getStartYPos() == startY
                && move.getEndXPos() == endX && move.getEndYPos() == endY;
    }

    /**
     * Returns the legal move matching the given from/to squares, or {@code null} if
     * no such move is legal here (so the caller falls back to the normal search).
     */
    private Move findLegalMove(List<Move> legalMoves, int startX, int startY, int endX, int endY) {
        for (Move move : legalMoves) {
            if (isMove(move, startX, startY, endX, endY)) {
                return move;
            }
        }
        return null;
    }
}
