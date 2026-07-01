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

    // Opening principle: don't develop a bishop directly IN FRONT of one of your own
    // pawns - it blocks the pawn (e.g. Bd3 before d4 is played) and cramps the whole
    // structure behind it. Penalised only during the opening (the first OPENING_PLIES
    // half-moves), when smooth development matters most; later such a bishop is
    // usually a deliberate choice, so the term switches off.
    private static final int BISHOP_BLOCKING_PAWN_PENALTY = 25;

    // ---- King safety (midgame only; fades to 0 as the board empties) ----
    // A king that has NOT castled sits in the centre where files open up: a small
    // nudge to get it tucked away. Kept modest so it never overrides a real capture.
    private static final int KING_NOT_CASTLED_PENALTY = 20;

    // For the three files around the king (its own file and the two neighbours) we
    // look at the friendly "shield" pawn in front of the king and penalise:
    //   - a MISSING shield pawn (that file has no friendly pawn to cover the king),
    //   - a shield pawn ADVANCED two or more ranks (it has marched up and left a hole;
    //     a single step like g3/h3 (g6/h6 for Black) is fine and is NOT penalised),
    //   - a fully OPEN file next to the king (no pawn of either colour: a highway for
    //     an enemy rook or queen straight at the king). This stacks with the missing
    //     penalty, so a wide-open file is scored as the worst case.
    private static final int SHIELD_MISSING_PENALTY = 15;
    private static final int SHIELD_ADVANCED_PENALTY = 12;
    private static final int OPEN_FILE_NEAR_KING_PENALTY = 15;

    // Bonus for making "luft" after castling kingside: the rook pawn nudged one step
    // (h3 for White, h6 for Black) gives the castled king an escape square, taking
    // the sting out of back-rank threats. Only counts when the king has actually
    // castled short (otherwise the h-pawn is not sheltering anything).
    private static final int CASTLED_LUFT_BONUS = 12;

    // ---- Piece activity / mobility (midgame; fades toward the endgame) ----
    // A small bonus per available move, for the side that can do more. Active pieces
    // control more squares and create more threats, so the engine is nudged to
    // develop and centralise rather than sit passively. Kept tiny (per move) so a
    // naturally busy piece like the queen can't outweigh real material or king
    // safety - it only breaks ties between otherwise-similar positions.
    private static final int MOBILITY_BONUS_PER_MOVE = 1;

    // Below this game phase we treat the position as an endgame and skip mobility
    // ENTIRELY - not just scale it down. Two reasons: it is a midgame idea (in the
    // endgame the side to move almost always has plenty of moves, so the count says
    // little), and counting every piece's moves at every leaf is the costliest part
    // of this term - exactly when few pieces remain and each has a long move list.
    // Skipping it there keeps the (now deeper) endgame search fast. Mirrors the
    // engine's own ENDGAME_PHASE threshold.
    private static final int MOBILITY_ENDGAME_PHASE = 6;

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
        score += blockingBishopPenalty(board, ply); // discourage a bishop parked in front of its own pawn early
        score += kingSafetyScore(board, phase, phaseMax); // penalise an exposed king (uncastled / weak pawn shield / open files)
        score += mobilityScore(board, phase, phaseMax); // reward the side whose pieces have more moves
        score += castledLuftScore(board, phase, phaseMax); // reward h3/h6 luft after castling short
        return score;
    }

    /**
     * Luft (escape-square) term: a small bonus for a side that has castled kingside
     * and nudged its rook pawn one step (h3 for White, h6 for Black), giving the king
     * a flight square against back-rank threats. Taken from White's point of view and
     * scaled by game phase, so it counts in the midgame and fades out as the board
     * empties (back-rank mates are a middlegame worry).
     *
     * @return a centipawn nudge from White's point of view
     */
    private int castledLuftScore(Cell[][] board, int phase, int phaseMax) {
        int raw = sideLuftBonus(board, Faction.WHITE) - sideLuftBonus(board, Faction.BLACK);
        return raw * phase / phaseMax; // full weight in the midgame, fading toward the endgame
    }

    /**
     * Returns {@link #CASTLED_LUFT_BONUS} when this side has castled kingside (its
     * king sits on the g-file home square, having moved) AND its rook pawn has been
     * pushed exactly one step to make luft (White h3, Black h6); otherwise 0.
     */
    private int sideLuftBonus(Cell[][] board, Faction side) {
        int homeRank = (side == Faction.WHITE) ? 0 : 7; // this side's back rank
        Piece king = board[6][homeRank].getContain(); // the short-castle king square (g-file)
        boolean castledShort = king instanceof King && king.getSide() == side && king.getMoveCount() > 0;
        if (!castledShort) {
            return 0; // h-pawn only shelters the king once it has castled short
        }
        int luftRank = (side == Faction.WHITE) ? 2 : 5; // h3 for White (y=2), h6 for Black (y=5)
        Piece pawn = board[7][luftRank].getContain(); // the h-file luft square
        boolean hasLuft = pawn instanceof Pawn && pawn.getSide() == side; // our own pawn nudged one step up
        return hasLuft ? CASTLED_LUFT_BONUS : 0;
    }

    /**
     * Piece-activity (mobility) term: rewards the side whose pieces can make more
     * moves. We count PSEUDO-LEGAL moves (each piece's own move list, which already
     * skips squares blocked by friendly pieces) rather than fully-legal ones - that
     * is far cheaper to compute at every leaf and is a good proxy for activity. The
     * net is taken from White's point of view and scaled by game phase so it counts
     * in the midgame and fades out as the board empties.
     *
     * @return a centipawn nudge from White's point of view
     */
    private int mobilityScore(Cell[][] board, int phase, int phaseMax) {
        if (phase <= MOBILITY_ENDGAME_PHASE) { // an endgame: skip mobility entirely (no bonus, and no costly counting)
            return 0;
        }
        int whiteMoves = countMoves(board, Faction.WHITE); // how many moves White's pieces can make
        int blackMoves = countMoves(board, Faction.BLACK); // how many moves Black's pieces can make
        int raw = (whiteMoves - blackMoves) * MOBILITY_BONUS_PER_MOVE; // + if White is busier
        return raw * phase / phaseMax; // full weight in the midgame, fading toward the endgame
    }

    /**
     * Counts the pseudo-legal moves available to all of one side's pieces: it asks
     * each piece for its own move list and sums the sizes. Mobility does not depend
     * on whose turn it is, so we can measure both sides straight from the board.
     */
    private int countMoves(Cell[][] board, Faction side) {
        int count = 0; // running total of available moves
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece == null || piece.getSide() != side) { // only this side's pieces
                    continue;
                }
                count += piece.move(board, x, y).size(); // its pseudo-legal moves
            }
        }
        return count; // total mobility for this side
    }

    /**
     * King-safety term: penalises an exposed king (not castled, a broken pawn shield,
     * or an open file pointing at it). Each side's penalty is measured as a positive
     * number, then combined from White's point of view (our own exposure hurts us).
     * The whole term is scaled by game phase so it counts in the midgame and fades to
     * nothing in the endgame, where the king is a fighter and wants to come out.
     *
     * @return a centipawn nudge from White's point of view
     */
    private int kingSafetyScore(Cell[][] board, int phase, int phaseMax) {
        int whitePenalty = sideKingPenalty(board, Faction.WHITE); // how exposed White's king is (>= 0)
        int blackPenalty = sideKingPenalty(board, Faction.BLACK); // how exposed Black's king is (>= 0)
        int raw = blackPenalty - whitePenalty; // White's own exposure is bad FOR White
        return raw * phase / phaseMax; // full weight in the midgame, fading toward the endgame
    }

    /**
     * Measures how exposed one side's king is, as a non-negative penalty (0 = safe).
     * Adds up three things: a flat penalty if the king has not castled, plus, for the
     * king's own file and the two adjacent files, a penalty for a missing or
     * over-advanced shield pawn and for a fully open file beside the king.
     *
     * A shield pawn one step off its home rank (g3/h3, or g6/h6 for Black) is treated
     * as fine - that is the normal luft move - so only pawns pushed TWO or more ranks
     * count as a hole.
     */
    private int sideKingPenalty(Cell[][] board, Faction side) {
        int penalty = 0; // a positive total: bigger = more exposed

        if (!kingHasCastled(board, side)) { // a king still in the centre is easier to attack
            penalty += KING_NOT_CASTLED_PENALTY;
        }

        int kingX = -1; // locate this side's king (only its file matters for the shield)
        for (int x = 0; x < 8 && kingX < 0; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (piece instanceof King && piece.getSide() == side) {
                    kingX = x;
                    break;
                }
            }
        }
        if (kingX < 0) { // no king found (should never happen) - nothing more to score
            return penalty;
        }

        int homeRank = (side == Faction.WHITE) ? 1 : 6; // the rank this side's pawns start on

        for (int f = kingX - 1; f <= kingX + 1; f++) { // the king's file and its two neighbours
            if (f < 0 || f > 7) { // a king on the a/h file has only two shield files
                continue;
            }

            boolean enemyPawnOnFile = false; // does the opponent have a pawn on this file?
            int shieldY = -1; // rank of the friendly pawn nearest the king (-1 = none)
            for (int y = 0; y < 8; y++) {
                Piece piece = board[f][y].getContain();
                if (!(piece instanceof Pawn)) {
                    continue;
                }
                if (piece.getSide() == side) {
                    // keep the friendly pawn closest to our own side (smallest advance)
                    boolean nearerKing = (side == Faction.WHITE) ? (shieldY < 0 || y < shieldY)
                                                                  : (shieldY < 0 || y > shieldY);
                    if (nearerKing) {
                        shieldY = y;
                    }
                } else {
                    enemyPawnOnFile = true;
                }
            }

            if (shieldY < 0) { // no friendly pawn shelters the king on this file
                penalty += SHIELD_MISSING_PENALTY;
                if (!enemyPawnOnFile) { // and no enemy pawn either: a fully open file at the king
                    penalty += OPEN_FILE_NEAR_KING_PENALTY;
                }
            } else {
                int advance = (side == Faction.WHITE) ? shieldY - homeRank : homeRank - shieldY; // ranks pushed from home
                if (advance >= 2) { // marched two or more ranks up: a hole in the shield
                    penalty += SHIELD_ADVANCED_PENALTY; // (a single step like g3/h3 / g6/h6 is fine)
                }
            }
        }
        return penalty; // total exposure for this king (0 = safe)
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
     * Opening principle: a bishop should not stand directly in front of a friendly
     * pawn, where it blocks the pawn's advance (a classic early mistake, e.g. Bd3
     * before d4 has been played). For the first {@link #OPENING_PLIES} half-moves we
     * penalise every friendly bishop sitting on the square immediately ahead of a
     * friendly pawn; after the opening the term switches off. Returned from White's
     * point of view.
     *
     * @param board the 8x8 grid of squares to inspect
     * @param ply   how many half-moves have been played so far
     * @return a centipawn nudge from White's point of view
     */
    private int blockingBishopPenalty(Cell[][] board, int ply) {
        if (ply >= OPENING_PLIES) { // past the opening: a blocking bishop is usually deliberate
            return 0;
        }
        int penalty = 0; // from White's perspective: negative hurts White, positive hurts Black
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (!(piece instanceof Pawn)) { // start from each pawn and look at the square ahead of it
                    continue;
                }
                boolean white = piece.getSide() == Faction.WHITE; // which way is "forward" for this pawn
                int frontY = white ? y + 1 : y - 1; // the rank directly in front of the pawn
                if (frontY < 0 || frontY > 7) { // pawn on the last rank (shouldn't happen): nothing ahead
                    continue;
                }
                Piece front = board[x][frontY].getContain(); // what sits right in front of the pawn
                if (front instanceof Bishop && front.getSide() == piece.getSide()) { // our own bishop blocking it
                    if (white) {
                        penalty -= BISHOP_BLOCKING_PAWN_PENALTY; // White blocking its own pawn hurts White
                    } else {
                        penalty += BISHOP_BLOCKING_PAWN_PENALTY; // Black's hurts Black (helps White)
                    }
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
