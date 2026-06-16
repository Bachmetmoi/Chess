package control.evaluate;

import entity.board.Cell; // one square of the board (knows its piece, if any)
import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.pieces.Bishop; // a minor piece, counted for development
import entity.pieces.King; // we check the king's square to detect castling
import entity.pieces.Knight; // a minor piece, counted for development
import entity.pieces.Pawn; // pawns are excluded from the "don't move twice" rule
import entity.pieces.Piece; // the abstract "a chess piece" type

/**
 * The MIDGAME-specific half of the evaluation. It owns the midgame king table
 * (where the king should stay tucked on the back rank behind its pawns) and the
 * two midgame extras: a bonus for having castled and a penalty for leaving minor
 * pieces undeveloped.
 *
 * {@link MainEvaluation} blends this class's king table with the endgame one by
 * game phase, and adds {@link #extras} on top.
 */
public class MiddleGameEvalation {

    // Bonus (centipawns) for a king that has castled. 50 is about half a pawn:
    // enough that the engine will castle when nothing more valuable is at stake,
    // but not so much that it ignores a real capture to do it.
    private static final int CASTLE_BONUS = 50;

    // Penalty (centipawns) for a knight or bishop still sitting on its starting
    // square. Moving an already-developed minor a SECOND time removes no penalty,
    // but bringing a NEW minor out does, so the bot is nudged to develop its whole
    // army before fiddling. It fades toward the endgame (scaled by game phase).
    private static final int UNDEVELOPED_MINOR_PENALTY = 15;

    // Opening principle: "don't move the same piece twice before the others are
    // developed." For the first OPENING_PLIES half-moves we penalise every NON-PAWN
    // piece that has already moved more than once, scaled by how many extra times it
    // has moved - so the engine prefers to bring out a NEW piece rather than shuffle
    // one it has already developed. This complements developmentScore (which
    // penalises pieces that have NOT moved at all). Pawns are excluded: the rule is
    // about piece development, not pawn play.
    private static final int OPENING_PLIES = 20; // roughly the first 10 moves by each side
    private static final int REPEATED_PIECE_MOVE_PENALTY = 10; // centipawns per extra move

    // MIDGAME king table (centipawns, White's view, [rank][file]): reward staying
    // tucked on the back rank (especially the castled g/b squares) and PUNISH
    // wandering up the board into danger.
    private static final int[][] KING_PST = {
            { 20, 30, 10, 0, 0, 10, 30, 20 }, // rank 1 (home): castled squares best
            { 20, 20, 0, 0, 0, 0, 20, 20 }, // rank 2
            { -10, -20, -20, -20, -20, -20, -20, -10 }, // rank 3
            { -20, -30, -30, -40, -40, -30, -30, -20 }, // rank 4
            { -30, -40, -40, -50, -50, -40, -40, -30 }, // rank 5
            { -30, -40, -40, -50, -50, -40, -40, -30 }, // rank 6
            { -30, -40, -40, -50, -50, -40, -40, -30 }, // rank 7
            { -30, -40, -40, -50, -50, -40, -40, -30 }, // rank 8
    };

    /**
     * The king's MIDGAME piece-square bonus for the square (rank, file), already
     * flipped into White's orientation by the caller. {@link MainEvaluation} blends
     * this with the endgame value.
     */
    public int kingSquareBonus(int rank, int file) {
        return KING_PST[rank][file];
    }

    /**
     * The midgame-only extras added on top of material and piece-square bonuses:
     * the castling bonus, the development penalty, and the opening "don't move a
     * piece twice" penalty. Returned from White's point of view.
     *
     * @param board    the 8x8 grid of squares to inspect
     * @param phase    the current game phase (phaseMax = midgame, 0 = bare endgame)
     * @param phaseMax the maximum phase value, used to scale the development term
     * @param ply      how many half-moves have been played (to gate the opening rule)
     * @return a centipawn nudge from White's point of view
     */
    public int extras(Cell[][] board, int phase, int phaseMax, int ply) {
        int score = 0; // from White's perspective
        if (kingHasCastled(board, Faction.WHITE)) { // has White's king castled?
            score += CASTLE_BONUS; // reward White
        }
        if (kingHasCastled(board, Faction.BLACK)) { // has Black's king castled?
            score -= CASTLE_BONUS; // reward Black
        }
        score += developmentScore(board, phase, phaseMax); // reward getting minors off their home squares
        score += repeatedMovePenalty(board, ply); // discourage shuffling one piece in the opening
        return score;
    }

    /**
     * Opening principle: don't move the same piece twice before developing the rest.
     * For the first {@link #OPENING_PLIES} half-moves, every non-pawn piece that has
     * already moved more than once is penalised by {@link #REPEATED_PIECE_MOVE_PENALTY}
     * per EXTRA move (a piece that has moved exactly once - i.e. just developed - is
     * fine). After the opening the term switches off entirely. Returned from White's
     * point of view.
     *
     * @param board the 8x8 grid of squares to inspect
     * @param ply   how many half-moves have been played so far
     * @return a centipawn nudge from White's point of view
     */
    private int repeatedMovePenalty(Cell[][] board, int ply) {
        if (ply >= OPENING_PLIES) { // past the opening: this is no longer a useful signal
            return 0;
        }
        int penalty = 0; // from White's perspective: negative hurts White, positive hurts Black
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece == null || piece instanceof Pawn) { // the rule is about pieces, not pawns
                    continue;
                }
                int extraMoves = piece.getMoveCount() - 1; // moves beyond the first (developing) move
                if (extraMoves <= 0) { // not yet moved, or moved only once: nothing to penalise
                    continue;
                }
                int amount = extraMoves * REPEATED_PIECE_MOVE_PENALTY; // grows the more it is shuffled
                if (piece.getSide() == Faction.WHITE) {
                    penalty -= amount; // White over-moving a piece hurts White
                } else {
                    penalty += amount; // Black's hurts Black (helps White)
                }
            }
        }
        return penalty;
    }

    /**
     * Rewards DEVELOPMENT: every knight or bishop still on its starting square (it
     * has never moved) is a small penalty for its owner. Once a minor has moved,
     * moving it again earns nothing here, but bringing out a NEW minor removes
     * another penalty, so the engine prefers to develop the rest of its army first.
     * The whole term is scaled by the game phase so it fades out in the endgame.
     *
     * @return a centipawn nudge from White's point of view
     */
    private int developmentScore(Cell[][] board, int phase, int phaseMax) {
        int penalty = 0; // from White's perspective: negative hurts White, positive hurts Black
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                boolean isMinor = piece instanceof Knight || piece instanceof Bishop; // only minors count
                if (isMinor && piece.getMoveCount() == 0) { // a minor that has never moved = undeveloped
                    if (piece.getSide() == Faction.WHITE) {
                        penalty -= UNDEVELOPED_MINOR_PENALTY; // White's own undeveloped minor hurts White
                    } else {
                        penalty += UNDEVELOPED_MINOR_PENALTY; // Black's hurts Black (helps White)
                    }
                }
            }
        }
        return penalty * phase / phaseMax; // full weight in the opening, fading toward the endgame
    }

    /**
     * Detects whether the given side has castled. We use a simple signal: castling
     * is the only normal way a king lands on its short-castle square (g-file, x=6)
     * or long-castle square (c-file, x=2) on its home rank having already moved.
     */
    private boolean kingHasCastled(Cell[][] board, Faction side) {
        int homeRank = (side == Faction.WHITE) ? 0 : 7; // White's back rank is y=0, Black's is y=7
        for (int x : new int[] { 2, 6 }) { // c-file (long castle) and g-file (short castle)
            Piece piece = board[x][homeRank].getContain(); // what sits on that castled square
            if (piece instanceof King && piece.getSide() == side && piece.getMoveCount() > 0) { // our king, and it has moved
                return true; // a moved king on a castle square means it castled
            }
        }
        return false; // king is not on a castled square (or hasn't moved) -> not castled
    }
}
