package control.evaluate;

import entity.board.Cell; // one square of the board (knows its piece, if any)
import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.pieces.King; // we locate both kings for the mop-up gradient
import entity.pieces.Piece; // the abstract "a chess piece" type

/**
 * The ENDGAME-specific half of the evaluation. It owns the endgame king table
 * (where the king should march to the centre and help) and the "mop-up" term that
 * actually forces checkmate once a side is clearly winning.
 *
 * {@link MainEvaluation} blends this class's king table with the midgame one by
 * game phase, and adds {@link #extras} on top.
 */
public class EndGameEvaluation {

    // We work in "centipawns" (a pawn = 100); the mop-up bonus below is in the same
    // units so it can nudge an already-won position without rivalling real material.
    private static final int CENTIPAWNS = 100;

    // The "mop-up" gradient only switches on in a clearly-won simplified endgame:
    // the phase must have fallen this low AND one side must be ahead by this much
    // material (in centipawns). Outside that, plain material decides and this is 0.
    private static final int ENDGAME_PHASE = 6;     // only mop up once phase has fallen this low
    private static final int MOP_UP_MIN_LEAD = 400; // and only when ahead by ~4 pawns of material

    // ENDGAME king table (centipawns, White's view, [rank][file]): now the centre
    // is BEST and the edges/corners worst, so the king is rewarded for stepping
    // forward to support pawns and help deliver mate.
    private static final int[][] KING_PST_ENDGAME = {
            { -50, -30, -30, -30, -30, -30, -30, -50 }, // rank 1
            { -30, -10, 0, 0, 0, 0, -10, -30 }, // rank 2
            { -30, 0, 20, 30, 30, 20, 0, -30 }, // rank 3
            { -30, 0, 30, 40, 40, 30, 0, -30 }, // rank 4
            { -30, 0, 30, 40, 40, 30, 0, -30 }, // rank 5
            { -30, 0, 20, 30, 30, 20, 0, -30 }, // rank 6
            { -30, -10, 0, 0, 0, 0, -10, -30 }, // rank 7
            { -50, -30, -30, -30, -30, -30, -30, -50 }, // rank 8
    };

    /**
     * The king's ENDGAME piece-square bonus for the square (rank, file), already
     * flipped into White's orientation by the caller. {@link MainEvaluation} blends
     * this with the midgame value.
     */
    public int kingSquareBonus(int rank, int file) {
        return KING_PST_ENDGAME[rank][file];
    }

    /**
     * The endgame-only extras added on top of material and piece-square bonuses:
     * just the mop-up gradient for now. Returned from White's point of view.
     *
     * @param board the 8x8 grid of squares to inspect
     * @param phase the current game phase (mop-up only triggers once it is low)
     * @return a centipawn nudge toward forcing mate, or 0 when it does not apply
     */
    public int extras(Cell[][] board, int phase) {
        return mopUpScore(board, phase);
    }

    /**
     * The "mop-up" evaluation that lets a winning side actually force mate. Plain
     * material gives no reason to prefer one winning move over another, so the
     * engine shuffles instead of mating. This term, active only in a clearly won
     * endgame, adds a small gradient that:
     *   - pushes the LOSING king toward a corner (its distance from the centre), and
     *   - walks the WINNING king closer to it (to deliver the mate).
     * The result is returned from White's point of view: positive helps White.
     *
     * @param board the 8x8 grid of squares to inspect
     * @param phase the current game phase; we only mop up once it is low enough
     * @return a centipawn nudge toward mating, or 0 when it does not apply
     */
    private int mopUpScore(Cell[][] board, int phase) {
        if (phase > ENDGAME_PHASE) { // still too many pieces around: no mop-up yet
            return 0;
        }

        int whiteMaterial = 0; // White's non-king material, in centipawns
        int blackMaterial = 0; // Black's non-king material, in centipawns
        int whiteKingX = -1, whiteKingY = -1; // White king's square (start "not found")
        int blackKingX = -1, blackKingY = -1; // Black king's square

        for (int x = 0; x < 8; x++) { // scan the whole board once
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece == null) {
                    continue; // empty square
                }
                if (piece instanceof King) { // remember where each king stands
                    if (piece.getSide() == Faction.WHITE) { whiteKingX = x; whiteKingY = y; }
                    else { blackKingX = x; blackKingY = y; }
                } else if (piece.getSide() == Faction.WHITE) {
                    whiteMaterial += piece.getValue() * CENTIPAWNS; // tally White's material
                } else {
                    blackMaterial += piece.getValue() * CENTIPAWNS; // tally Black's material
                }
            }
        }

        int lead = whiteMaterial - blackMaterial; // > 0: White is ahead; < 0: Black is ahead
        if (Math.abs(lead) < MOP_UP_MIN_LEAD) { // not a decisive lead: nothing to mop up
            return 0;
        }

        // The side with more material is the "winner" trying to mate the other king.
        boolean whiteWinning = lead > 0; // which king are we hunting?
        int loserKingX = whiteWinning ? blackKingX : whiteKingX; // king being driven to the edge
        int loserKingY = whiteWinning ? blackKingY : whiteKingY;
        int winnerKingX = whiteWinning ? whiteKingX : blackKingX; // king coming to help
        int winnerKingY = whiteWinning ? whiteKingY : blackKingY;

        // How far the losing king is from the centre (0 in the middle, up to 6 in a
        // corner). Bigger is better for the winner: a cornered king is easier to mate.
        int loserCornerDist = centreDistance(loserKingX, loserKingY);
        // How far apart the kings are (Manhattan distance, 0..14). We want this SMALL
        // so the winning king escorts its pieces in for the kill.
        int kingsApart = Math.abs(winnerKingX - loserKingX) + Math.abs(winnerKingY - loserKingY);

        // Classic mop-up weights: reward a cornered enemy king and a nearby own king.
        int bonus = (int) (4.7 * loserCornerDist + 1.6 * (14 - kingsApart));
        return whiteWinning ? bonus : -bonus; // sign it from White's perspective
    }

    /**
     * Distance of a square from the centre of the board, as the sum of its file and
     * rank distances from the two central lines. Returns 0 for the four central
     * squares and grows to 6 in the corners. Used by {@link #mopUpScore}.
     */
    private int centreDistance(int x, int y) {
        int fileDist = Math.max(3 - x, x - 4); // 0 on files d/e, up to 3 on the a/h files
        int rankDist = Math.max(3 - y, y - 4); // 0 on ranks 4/5, up to 3 on ranks 1/8
        return fileDist + rankDist; // combined, 0 (centre) .. 6 (corner)
    }
}
