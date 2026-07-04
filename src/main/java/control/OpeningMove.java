package control; // lives with the engine and the other game-logic classes

import java.util.ArrayList; // collects the book moves that are actually legal right now
import java.util.List; // the List interface type we pass around
import java.util.Random; // picks one of the candidate openings at random

import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.move.Move; // the abstract "a move" type we hand back to the engine
import entity.state.GameState; // holds whose turn it is and the move history

/**
 * A tiny opening picker for WHITE's very first move.
 *
 * When the engine plays White from the standard starting position, searching is
 * wasted effort: any mainstream first move is fine, and always playing the same
 * one makes the engine predictable. So instead we pick, at random, one of four
 * classic openings:
 *
 *   1.e4  (king's pawn),  1.d4  (queen's pawn),
 *   1.Nf3 (Reti),         1.Nc3 (Van Geet)
 *
 * Coordinates follow the project's board[x][y] convention: x = file (a=0 .. h=7)
 * and y = rank (rank 1 = 0 .. rank 8 = 7).
 */
public class OpeningMove {
    // The four candidate first moves, each as {startX, startY, endX, endY}.
    private static final int[][] WHITE_FIRST_MOVES = {
            {4, 1, 4, 3}, // 1.e4  (e2-e4)
            {3, 1, 3, 3}, // 1.d4  (d2-d4)
            {6, 0, 5, 2}, // 1.Nf3 (g1-f3)
            {1, 0, 2, 2}, // 1.Nc3 (b1-c3)
    };

    private final Random random = new Random(); // the dice we roll to choose an opening

    /**
     * Picks White's first move at random from the candidates above.
     *
     * Only fires when it really is the first move of the game (White to move and
     * an empty move history); in every other position it returns {@code null} so
     * the engine falls back to its normal search.
     *
     * @param state      the current game state (turn + move history)
     * @param legalMoves every legal move for the side to move right now
     * @return one of the four openings, or {@code null} if none applies here
     */
    public Move chooseFirstMove(GameState state, List<Move> legalMoves) {
        if (state.getTurn() != Faction.WHITE) { // this picker only covers White's first move
            return null;
        }
        if (!state.getMoveHistory().isEmpty()) { // not the first move of the game
            return null;
        }

        // Keep only the candidates that are legal on the actual board. From the
        // standard start all four are, but checking keeps us safe anyway (and a
        // customized game turns the book off before we are even asked).
        List<Move> candidates = new ArrayList<>();
        for (int[] coords : WHITE_FIRST_MOVES) {
            Move move = findLegalMove(legalMoves, coords[0], coords[1], coords[2], coords[3]);
            if (move != null) {
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) { // none of the openings is legal: let the search decide
            return null;
        }
        return candidates.get(random.nextInt(candidates.size())); // roll the dice and play it
    }

    /**
     * Returns the legal move matching the given from/to squares, or {@code null}
     * if no such move is legal here.
     */
    private Move findLegalMove(List<Move> legalMoves, int startX, int startY, int endX, int endY) {
        for (Move move : legalMoves) {
            if (move.getStartXPos() == startX && move.getStartYPos() == startY
                    && move.getEndXPos() == endX && move.getEndYPos() == endY) {
                return move;
            }
        }
        return null;
    }
}
