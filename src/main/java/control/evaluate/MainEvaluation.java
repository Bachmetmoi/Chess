package control.evaluate;

import entity.board.Cell; // one square of the board (knows its piece, if any)
import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.pieces.Bishop; // bishops get a per-square bonus (piece-square table)
import entity.pieces.King; // the king's table is blended between game phases
import entity.pieces.Knight; // knights get a per-square bonus (piece-square table)
import entity.pieces.Pawn; // pawns get a per-square bonus (piece-square table)
import entity.pieces.Piece; // the abstract "a chess piece" type
import entity.pieces.Queen; // queens get a per-square bonus (piece-square table)
import entity.pieces.Rook; // rooks get a per-square bonus (piece-square table)

/**
 * The single entry point for scoring a QUIET leaf position at the bottom of the
 * minimax search. It returns a score in "centipawns" from White's point of
 * view:
 * a positive number means White is better, a negative number means Black is.
 *
 * This class owns everything that does NOT depend on the game phase - raw
 * material, the five non-king piece-square tables, and the game-phase count -
 * and
 * it orchestrates the two phase-specific evaluators:
 * {@link MiddleGameEvalation} (midgame king table + castling + development) and
 * {@link EndGameEvaluation} (endgame king table + mop-up).
 * The king's table is "tapered": blended between the two phase tables by how
 * much
 * material is still on the board, and the phase-specific extras fade in and out
 * the same way.
 */
public class MainEvaluation {

    // We work in "centipawns": a pawn is worth 100, so smaller positional bonuses
    // (half a pawn = 50, ...) have room to nudge ties without ever outweighing a
    // whole piece. Multiplying each Piece's value by this keeps that convention.
    private static final int CENTIPAWNS = 100;

    // A bishop is worth slightly MORE than a knight (3.12 vs 3.00 pawns = 312 cp).
    // The two pieces share the same integer value (3) for move ordering, but here -
    // where actual trade decisions are scored - the small edge stops the engine
    // from
    // casually swapping its bishop for a knight in the opening (the bishop pair and
    // long-range reach make it the better minor in most open positions).
    private static final int BISHOP_VALUE = 312;

    // The game-phase total at the start of a game (a full board). Each non-pawn
    // piece adds a phase weight (minor = 1, rook = 2, queen = 4); per side that is
    // 2+2+1+1 +2+2 +4 = 12, times two sides = 24. As pieces are traded the total
    // falls toward 0 (a bare endgame). Used to blend the two king tables.
    private static final int PHASE_MAX = 24;

    // Bonus (centipawns) for a rook standing on a file with no pawns at all: an
    // "open file" is a highway for the rook, so this is a meaningful nudge.
    private static final int ROOK_OPEN_FILE_BONUS = 25;

    // Bonus for a rook on a "half-open" file: no friendly pawns block it, but the
    // enemy still has a pawn there. Useful (the rook pressures that pawn) but worth
    // less than a fully open file, so about half the bonus.
    private static final int ROOK_HALF_OPEN_FILE_BONUS = 12;

    // Bonus for a ROOK BATTERY: two (or more) friendly rooks doubled on the same
    // file. They defend each other and pile pressure down the file, so a pair is
    // much stronger than two scattered rooks. Awarded per extra rook on the file, so
    // a normal pair earns it once; it stacks with the open-file bonus above, so
    // doubled rooks on an open file are rewarded most of all.
    private static final int ROOK_BATTERY_BONUS = 20;

    // Bonus for CONNECTED ROOKS: two friendly rooks on the same RANK (row) with no
    // pawn between them, so they defend each other and sweep the rank together
    // (typically the connected rooks you get right after castling and clearing the
    // back rank). Same "a pawn blocks but a piece can step aside" rule as the file
    // battery above.
    private static final int ROOK_CONNECTED_RANK_BONUS = 15;

    // ---- Pawn structure ----
    // Penalty for an ISOLATED pawn: one with no friendly pawn on either adjacent
    // file, so no pawn can ever defend it. It is a long-term weakness (a target the
    // enemy can pile up on), hence a small standing penalty in every phase.
    private static final int ISOLATED_PAWN_PENALTY = 12;

    // Bonus for a CONNECTED pawn: one supported by a friendly pawn on an adjacent
    // file right beside it - either side by side (a phalanx) or diagonally behind
    // (a defender). Connected pawns protect each other and control key squares, so
    // each such pawn earns a small bonus.
    private static final int CONNECTED_PAWN_BONUS = 8;

    // Penalty for a DOUBLED pawn: two (or more) friendly pawns stacked on the same
    // file. They cannot defend each other, get in each other's way, and the file is
    // half-crippled. Charged per EXTRA pawn on the file (so a normal double costs it
    // once, a triple twice).
    private static final int DOUBLED_PAWN_PENALTY = 15;

    // ---- Passed-pawn bonuses (the key to converting a won endgame) ----
    // A pawn is "passed" when no enemy pawn can stop it: none on its own file or
    // either adjacent file stands ahead of it. Indexed by the pawn's RELATIVE rank
    // (how far it has advanced from its own side: 1 = just off the start ... 6 =
    // one
    // step from promoting). The BASE applies in every phase; the ENDGAME push bonus
    // is added on top and scaled up as the board empties, because a passer is far
    // likelier to actually queen once few pieces remain. This is what gives the
    // search a gradient to MARCH a passer home instead of shuffling into a draw.
    private static final int[] PASSED_PAWN_BASE = { 0, 5, 10, 20, 35, 60, 90, 0 };
    private static final int[] PASSED_PAWN_ENDGAME = { 0, 5, 15, 30, 55, 90, 140, 0 };

    // Centipawns per square of king "escort" advantage on a passed pawn: if the
    // pushing side's king stands closer to the pawn than the defending king, the
    // pawn is far easier to shepherd home (or harder to stop). Endgame only, so it
    // is scaled by phase like the push bonus above.
    private static final int PASSED_PAWN_KING_ESCORT = 6;

    // Piece-square table for pawns, in centipawns, from White's point of view.
    // Read it as PAWN_PST[rank][file]: rank 0 = White's back rank ... rank 7 = the
    // promotion rank; file 0 = a-file ... 7 = h-file. Black pawns read it flipped
    // top-to-bottom (7 - y), which mirrors the values onto Black's side.
    private static final int[][] PAWN_PST = {
            { 0, 0, 0, 0, 0, 0, 0, 0 }, // rank 1 (pawns never stand here)
            { 5, 10, 10, -20, -20, 10, 10, 5 }, // rank 2 (start): d2/e2 discouraged
            { 5, -5, -10, 0, 0, -10, -5, 5 }, // rank 3
            { 0, 0, 0, 20, 20, 0, 0, 0 }, // rank 4: d4/e4 rewarded
            { 5, 5, 10, 25, 25, 10, 5, 5 }, // rank 5
            { 10, 10, 20, 30, 30, 20, 10, 10 }, // rank 6: well advanced, push it
            { 50, 50, 50, 50, 50, 50, 50, 50 }, // rank 7 (about to promote)
            { 0, 0, 0, 0, 0, 0, 0, 0 }, // rank 8 (promotion handled by material)
    };

    // Knight table (centipawns, White's view, [rank][file]). Knights love the
    // centre (+20) and hate the rim/corners ("a knight on the rim is dim").
    private static final int[][] KNIGHT_PST = {
            { -50, -40, -30, -30, -30, -30, -40, -50 }, // rank 1
            { -40, -20, 0, 0, 0, 0, -20, -40 }, // rank 2
            { -30, 5, 10, 15, 15, 10, 5, -30 }, // rank 3
            { -30, 0, 15, 20, 20, 15, 0, -30 }, // rank 4
            { -30, 5, 15, 20, 20, 15, 5, -30 }, // rank 5
            { -30, 0, 10, 15, 15, 10, 0, -30 }, // rank 6
            { -40, -20, 0, 5, 5, 0, -20, -40 }, // rank 7
            { -50, -40, -30, -30, -30, -30, -40, -50 }, // rank 8
    };

    // Bishop table (centipawns, White's view, [rank][file]). Bishops want long
    // open diagonals and the centre (+10), with a reward for the fianchetto
    // squares b2/g2 (rank 2) and a penalty for the edges.
    private static final int[][] BISHOP_PST = {
            { -20, -10, -10, -10, -10, -10, -10, -20 }, // rank 1
            { -10, 5, 0, 5, 5, 0, 5, -10 }, // rank 2 (fianchetto b2/g2)
            { -10, 10, 10, 10, 10, 10, 10, -10 }, // rank 3
            { -10, 0, 10, 10, 10, 10, 0, -10 }, // rank 4
            { -10, 5, 5, 10, 10, 5, 5, -10 }, // rank 5
            { -10, 0, 5, 10, 10, 5, 0, -10 }, // rank 6
            { -10, 0, 0, 0, 0, 0, 0, -10 }, // rank 7
            { -20, -10, -10, -10, -10, -10, -10, -20 }, // rank 8
    };

    // Rook table (centipawns, White's view, [rank][file]). Rooks belong on open
    // files and especially the 7th rank (rank index 6), where they cramp the enemy
    // king; the back-rank centre files get a small nudge for
    // connecting/centralising.
    private static final int[][] ROOK_PST = {
            { 0, 0, 0, 5, 5, 0, 0, 0 }, // rank 1
            { -5, 0, 0, 0, 0, 0, 0, -5 }, // rank 2
            { -5, 0, 0, 0, 0, 0, 0, -5 }, // rank 3
            { -5, 0, 0, 0, 0, 0, 0, -5 }, // rank 4
            { -5, 0, 0, 0, 0, 0, 0, -5 }, // rank 5
            { -5, 0, 0, 0, 0, 0, 0, -5 }, // rank 6
            { 5, 10, 10, 10, 10, 10, 10, 5 }, // rank 7
            { 0, 0, 0, 0, 0, 0, 0, 0 }, // rank 8
    };

    // Queen table (centipawns, White's view, [rank][file]). A mild pull toward the
    // centre, discouraging the corners and an early sortie to the edge.
    private static final int[][] QUEEN_PST = {
            { -20, -10, -10, -5, -5, -10, -10, -20 }, // rank 1
            { -10, 0, 5, 0, 0, 0, 0, -10 }, // rank 2
            { -10, 5, 5, 5, 5, 5, 0, -10 }, // rank 3
            { -5, 0, 5, 5, 5, 5, 0, -5 }, // rank 4
            { 0, 0, 5, 5, 5, 5, 0, -5 }, // rank 5
            { -10, 5, 5, 5, 5, 5, 0, -10 }, // rank 6
            { -10, 0, 5, 0, 0, 0, 0, -10 }, // rank 7
            { -20, -10, -10, -5, -5, -10, -10, -20 }, // rank 8
    };

    // The two phase-specific evaluators we delegate to for the king table and the
    // phase-specific extras (castling/development vs. mop-up).
    private final MiddleGameEvalation midGame = new MiddleGameEvalation();
    private final EndGameEvaluation endGame = new EndGameEvaluation();

    /**
     * Scores the whole position: material + piece-square bonuses (king tapered by
     * phase) + the midgame and endgame extras. Returned from White's point of view.
     *
     * @param board the 8x8 grid of squares to score
     * @param ply   how many half-moves have been played (gates the opening-only
     *              terms)
     * @return the position value in centipawns (positive = White is better)
     */
    public int evaluate(Cell[][] board, int ply) {
        int phase = gamePhase(board); // PHASE_MAX = full board, 0 = bare endgame
        int score = pieceRawMaterial(board); // raw material is the same in any phase
        score += pieceSquareScore(board, phase); // positional bonuses, king blended by phase
        score += rookFileScore(board); // reward rooks on open / half-open files
        score += rookBatteryScore(board); // reward doubled rooks on the same file
        score += rookConnectedRankScore(board); // reward connected rooks on the same rank
        score += pawnStructureScore(board); // penalise isolated pawns, reward connected ones
        score += doubledPawnScore(board); // penalise doubled pawns on a file
        score += passedPawnScore(board, phase); // reward passed pawns (push bonus grows toward the endgame)
        score += midGame.extras(board, phase, PHASE_MAX, ply); // castling + development + opening principles
        score += endGame.extras(board, phase); // mop-up gradient (only in a won endgame)
        return score;
    }

    /**
     * Adds up the raw material on the board: every White piece counts as a plus
     * and every Black piece as a minus, each scaled to centipawns. The kings cancel
     * out (both sides always have exactly one), so they make no difference here.
     *
     * @param board the 8x8 grid of squares to score
     * @return the material balance in centipawns, from White's point of view
     */
    public int pieceRawMaterial(Cell[][] board) {
        int score = 0; // running total, from White's perspective
        for (int x = 0; x < 8; x++) { // walk every file (column)
            for (int y = 0; y < 8; y++) { // walk every rank (row)
                Piece piece = board[x][y].getContain(); // the piece on this square, or null if empty
                if (piece == null) { // empty square contributes nothing
                    continue; // skip to the next square
                }
                int value = materialValue(piece); // this piece's worth in centipawns
                if (piece.getSide() == Faction.WHITE) { // a White piece helps White
                    score += value; // so add its value
                } else { // a Black piece helps Black
                    score -= value; // so subtract its value
                }
            }
        }
        return score; // total material balance (positive = White ahead)
    }

    /**
     * This piece's material worth in centipawns. Everything uses its plain
     * {@link Piece#getValue} times {@link #CENTIPAWNS}, except the bishop, which is
     * nudged to {@link #BISHOP_VALUE} (312) so the engine values it just above a
     * knight and won't trade the two off for free.
     */
    private int materialValue(Piece piece) {
        if (piece instanceof Bishop) {
            return BISHOP_VALUE; // a touch more than a knight's 300
        }
        return piece.getValue() * CENTIPAWNS; // every other piece keeps its plain value
    }

    /**
     * Sums every piece's piece-square bonus. Non-king pieces read their own fixed
     * table; the king blends its midgame and endgame tables by the game phase, so
     * it hides on the back rank early and marches to the centre in the endgame.
     *
     * @param board the 8x8 grid of squares to score
     * @param phase the current game phase (PHASE_MAX = midgame, 0 = bare endgame)
     * @return the positional bonus total in centipawns, from White's point of view
     */
    private int pieceSquareScore(Cell[][] board, int phase) {
        int score = 0; // running total, from White's perspective
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece == null) { // empty square contributes nothing
                    continue;
                }
                int rank = (piece.getSide() == Faction.WHITE) ? y : 7 - y; // flip the rank for Black
                int bonus; // this piece's positional bonus, in White's orientation
                if (piece instanceof King) { // the king's value depends on the game phase
                    int mid = midGame.kingSquareBonus(rank, x); // value if it were still a midgame
                    int end = endGame.kingSquareBonus(rank, x); // value in a pure endgame
                    // Blend by how much material is left: midgame value at PHASE_MAX,
                    // endgame value at 0, smoothly in between.
                    bonus = (mid * phase + end * (PHASE_MAX - phase)) / PHASE_MAX;
                } else {
                    bonus = nonKingSquareBonus(piece, rank, x); // a fixed table per piece type
                }
                if (piece.getSide() == Faction.WHITE) { // a good square helps the owner
                    score += bonus; // add it for White
                } else {
                    score -= bonus; // subtract it for Black
                }
            }
        }
        return score; // total positional bonus
    }

    /**
     * Looks up the (phase-independent) piece-square bonus for a non-king piece on
     * the square (rank, file), already flipped into White's orientation by the
     * caller. Anything without a table scores 0.
     */
    private int nonKingSquareBonus(Piece piece, int rank, int file) {
        int[][] table; // the table that matches this piece type
        if (piece instanceof Pawn) {
            table = PAWN_PST;
        } else if (piece instanceof Knight) {
            table = KNIGHT_PST;
        } else if (piece instanceof Bishop) {
            table = BISHOP_PST;
        } else if (piece instanceof Rook) {
            table = ROOK_PST;
        } else if (piece instanceof Queen) {
            table = QUEEN_PST;
        } else { // anything else has no table
            return 0;
        }
        return table[rank][file]; // the bonus for this square, in White's orientation
    }

    /**
     * Rewards rooks on open and half-open files. A file (column) is "open" when no
     * pawns of either colour stand on it, and "half-open" for a side when only the
     * ENEMY has a pawn there. An open file turns the rook into a long-range cannon;
     * a half-open file lets it lean on the enemy pawn. A rook blocked by its own
     * pawn earns nothing here.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the rook-file bonus total in centipawns, from White's point of view
     */
    private int rookFileScore(Cell[][] board) {
        int[] whitePawns = new int[8]; // how many White pawns stand on each file (column)
        int[] blackPawns = new int[8]; // how many Black pawns stand on each file
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece instanceof Pawn) { // only pawns decide whether a file is open
                    if (piece.getSide() == Faction.WHITE) {
                        whitePawns[x]++;
                    } else {
                        blackPawns[x]++;
                    }
                }
            }
        }

        int score = 0; // running total, from White's perspective
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (!(piece instanceof Rook)) { // only rooks get the file bonus
                    continue;
                }
                boolean white = piece.getSide() == Faction.WHITE; // whose rook this is
                boolean ownPawnOnFile = (white ? whitePawns[x] : blackPawns[x]) > 0; // a friendly pawn blocks it
                boolean enemyPawnOnFile = (white ? blackPawns[x] : whitePawns[x]) > 0; // an enemy pawn on the file

                int bonus; // this rook's file bonus, "good for the owner"
                if (ownPawnOnFile) {
                    bonus = 0; // blocked by our own pawn: no open-file value
                } else if (enemyPawnOnFile) {
                    bonus = ROOK_HALF_OPEN_FILE_BONUS; // half-open: only the enemy pawn is in the way
                } else {
                    bonus = ROOK_OPEN_FILE_BONUS; // fully open file
                }

                if (white) { // a good file helps the owner
                    score += bonus; // add it for White
                } else {
                    score -= bonus; // subtract it for Black
                }
            }
        }
        return score; // total rook-file bonus
    }

    /**
     * Rewards ROOK BATTERIES: two or more friendly rooks doubled on the same file
     * with NO PAWN standing between them. Doubled rooks defend each other and pile
     * pressure down the file, but only if the file between them is not blocked by a
     * pawn - a piece in between is fine (it can step aside), a pawn is not. We award
     * {@link #ROOK_BATTERY_BONUS} for each adjacent pair of friendly rooks on a file
     * whose connecting squares hold no pawn (so a normal pair earns it once, three
     * clear-stacked rooks twice). Returned from White's point of view; this stacks
     * with {@link #rookFileScore}, so a battery on an open file collects both bonuses.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the rook-battery bonus total in centipawns, from White's point of view
     */
    private int rookBatteryScore(Cell[][] board) {
        int score = 0; // from White's perspective
        score += fileBatteryBonus(board, Faction.WHITE); // White's batteries help White
        score -= fileBatteryBonus(board, Faction.BLACK); // Black's batteries help Black
        return score; // total rook-battery bonus
    }

    /**
     * Sums one side's rook-battery bonuses: for every file, pairs each friendly rook
     * with the next friendly rook above it and awards {@link #ROOK_BATTERY_BONUS} when
     * no pawn sits between them. Always returns a non-negative total (the caller
     * applies the sign for the side).
     */
    private int fileBatteryBonus(Cell[][] board, Faction side) {
        int total = 0; // running bonus for this side
        for (int x = 0; x < 8; x++) { // every file
            int prevRookY = -1; // rank of the last friendly rook seen on this file (-1 = none yet)
            for (int y = 0; y < 8; y++) { // scan the file from rank 1 upward
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece instanceof Rook && piece.getSide() == side) { // a friendly rook
                    if (prevRookY >= 0 && noPawnBetween(board, x, prevRookY, y)) { // doubled with a clear path
                        total += ROOK_BATTERY_BONUS; // they form a working battery
                    }
                    prevRookY = y; // remember this rook for the next pairing
                }
            }
        }
        return total; // this side's battery bonus (>= 0)
    }

    /**
     * True if no pawn (of either colour) stands on file {@code x} strictly between
     * ranks {@code y1} and {@code y2}. A pawn blocks the file and breaks the battery;
     * any other piece is allowed, since it can move aside to clear the rooks' line.
     */
    private boolean noPawnBetween(Cell[][] board, int x, int y1, int y2) {
        int lo = Math.min(y1, y2) + 1; // first square strictly between the rooks
        int hi = Math.max(y1, y2);     // one past the last in-between square
        for (int y = lo; y < hi; y++) {
            if (board[x][y].getContain() instanceof Pawn) { // a pawn in the way
                return false;
            }
        }
        return true; // clear of pawns
    }

    /**
     * Rewards CONNECTED ROOKS: two friendly rooks on the same RANK (row) with no pawn
     * between them, so they protect each other along the rank. We pair each rook with
     * the next friendly rook to its right on the same rank and award
     * {@link #ROOK_CONNECTED_RANK_BONUS} when the squares between hold no pawn (a piece
     * is allowed - it can step aside). Returned from White's point of view.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the connected-rooks bonus total in centipawns, from White's point of view
     */
    private int rookConnectedRankScore(Cell[][] board) {
        int score = 0; // from White's perspective
        score += rankConnectedBonus(board, Faction.WHITE); // White's connected rooks help White
        score -= rankConnectedBonus(board, Faction.BLACK); // Black's help Black
        return score;
    }

    /**
     * Sums one side's connected-rooks bonuses: for every rank, pairs each friendly
     * rook with the next friendly rook to its right and awards
     * {@link #ROOK_CONNECTED_RANK_BONUS} when no pawn sits between them. Always
     * non-negative (the caller applies the sign).
     */
    private int rankConnectedBonus(Cell[][] board, Faction side) {
        int total = 0; // running bonus for this side
        for (int y = 0; y < 8; y++) { // every rank
            int prevRookX = -1; // file of the last friendly rook seen on this rank (-1 = none yet)
            for (int x = 0; x < 8; x++) { // scan the rank from the a-file rightward
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece instanceof Rook && piece.getSide() == side) { // a friendly rook
                    if (prevRookX >= 0 && noPawnBetweenOnRank(board, y, prevRookX, x)) { // connected with a clear path
                        total += ROOK_CONNECTED_RANK_BONUS;
                    }
                    prevRookX = x; // remember this rook for the next pairing
                }
            }
        }
        return total; // this side's connected-rooks bonus (>= 0)
    }

    /**
     * True if no pawn (of either colour) stands on rank {@code y} strictly between
     * files {@code x1} and {@code x2}. Mirrors {@link #noPawnBetween} along a rank: a
     * pawn breaks the connection, any other piece is allowed.
     */
    private boolean noPawnBetweenOnRank(Cell[][] board, int y, int x1, int x2) {
        int lo = Math.min(x1, x2) + 1; // first square strictly between the rooks
        int hi = Math.max(x1, x2);     // one past the last in-between square
        for (int x = lo; x < hi; x++) {
            if (board[x][y].getContain() instanceof Pawn) { // a pawn in the way
                return false;
            }
        }
        return true; // clear of pawns
    }

    /**
     * Penalises DOUBLED PAWNS: two or more friendly pawns on the same file. We count
     * each side's pawns per file and charge {@link #DOUBLED_PAWN_PENALTY} per EXTRA
     * pawn (so a normal double costs it once, a triple twice). Returned from White's
     * point of view, and phase-independent - doubled pawns are a weakness all game.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the doubled-pawn penalty total in centipawns, from White's point of view
     */
    private int doubledPawnScore(Cell[][] board) {
        int[] whitePawns = new int[8]; // White pawns on each file
        int[] blackPawns = new int[8]; // Black pawns on each file
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (piece instanceof Pawn) {
                    if (piece.getSide() == Faction.WHITE) {
                        whitePawns[x]++;
                    } else {
                        blackPawns[x]++;
                    }
                }
            }
        }

        int score = 0; // from White's perspective
        for (int x = 0; x < 8; x++) {
            if (whitePawns[x] >= 2) { // White stacked pawns on this file
                score -= (whitePawns[x] - 1) * DOUBLED_PAWN_PENALTY; // a White weakness hurts White
            }
            if (blackPawns[x] >= 2) { // Black stacked pawns on this file
                score += (blackPawns[x] - 1) * DOUBLED_PAWN_PENALTY; // a Black weakness helps White
            }
        }
        return score; // total doubled-pawn penalty
    }

    /**
     * Scores PAWN STRUCTURE: a penalty for each isolated pawn and a bonus for each
     * connected one, summed from White's point of view (a White weakness lowers the
     * score, a Black weakness raises it). These are phase-independent: a pawn
     * skeleton
     * is a long-term feature that matters from the opening to the endgame.
     *
     * A pawn is ISOLATED when neither adjacent file holds a friendly pawn, so
     * nothing
     * can ever defend it. A pawn is CONNECTED when a friendly pawn sits on an
     * adjacent
     * file right beside it - level with it (a phalanx) or one rank behind it (a
     * defender). The two are mutually exclusive: a connected pawn always has a
     * neighbour on an adjacent file, so it is never isolated.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the pawn-structure total in centipawns, from White's point of view
     */
    private int pawnStructureScore(Cell[][] board) {
        boolean[] whiteOnFile = new boolean[8]; // does White have a pawn anywhere on this file?
        boolean[] blackOnFile = new boolean[8]; // does Black have a pawn anywhere on this file?
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (piece instanceof Pawn) {
                    if (piece.getSide() == Faction.WHITE) {
                        whiteOnFile[x] = true;
                    } else {
                        blackOnFile[x] = true;
                    }
                }
            }
        }

        int score = 0; // from White's perspective
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (!(piece instanceof Pawn)) {
                    continue; // only pawns have structure
                }
                Faction side = piece.getSide();
                boolean white = side == Faction.WHITE;
                boolean[] ownFiles = white ? whiteOnFile : blackOnFile; // our own pawns, by file

                // Isolated: no friendly pawn on either neighbouring file.
                boolean hasNeighbourFile = (x > 0 && ownFiles[x - 1]) || (x < 7 && ownFiles[x + 1]);

                // Connected: a friendly pawn on an adjacent file, level with us (phalanx)
                // or one rank behind us defending (white defends from y-1, black from y+1).
                int supportRank = white ? y - 1 : y + 1;
                boolean connected = isFriendlyPawn(board, x - 1, y, side)
                        || isFriendlyPawn(board, x + 1, y, side)
                        || isFriendlyPawn(board, x - 1, supportRank, side)
                        || isFriendlyPawn(board, x + 1, supportRank, side);

                int bonus = 0; // this pawn's structure value, "good for the owner"
                if (!hasNeighbourFile) {
                    bonus -= ISOLATED_PAWN_PENALTY; // no friend can ever defend it
                }
                if (connected) {
                    bonus += CONNECTED_PAWN_BONUS; // supported by a neighbour
                }

                if (white) {
                    score += bonus; // a White feature, from White's point of view
                } else {
                    score -= bonus; // a Black feature flips sign
                }
            }
        }
        return score; // total pawn-structure score
    }

    /**
     * True if a friendly pawn (of {@code side}) stands on (x, y). Off-board squares
     * return false, so callers can probe neighbours without bounds-checking first.
     */
    private boolean isFriendlyPawn(Cell[][] board, int x, int y, Faction side) {
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            return false; // off the edge of the board
        }
        Piece piece = board[x][y].getContain();
        return piece instanceof Pawn && piece.getSide() == side;
    }

    /**
     * Rewards PASSED PAWNS - the single most important endgame term. A pawn with no
     * enemy pawn able to stop it (none on its own file or either adjacent file
     * ahead
     * of it) gets a bonus that climbs steeply as it nears promotion. The bonus has
     * a
     * phase-independent BASE plus an ENDGAME part (a bigger push bonus and a
     * king-escort term) that grows as the board empties - this is what gives the
     * search a clear gradient to march a passer to queen instead of shuffling into
     * a
     * draw in a won ending.
     *
     * @param board the 8x8 grid of squares to inspect
     * @param phase the current game phase (PHASE_MAX = midgame, 0 = bare endgame)
     * @return the passed-pawn bonus total in centipawns, from White's point of view
     */
    private int passedPawnScore(Cell[][] board, int phase) {
        // For each file, the enemy pawn we must clear to count as "passed": a White
        // pawn is blocked by Black pawns AHEAD (a higher rank), a Black pawn by White
        // pawns ahead (a lower rank). So track the highest Black pawn and the lowest
        // White pawn on each file. Also remember the kings, for the escort term.
        int[] blackMaxRank = { -1, -1, -1, -1, -1, -1, -1, -1 }; // -1 = no Black pawn on this file
        int[] whiteMinRank = { 8, 8, 8, 8, 8, 8, 8, 8 }; // 8 = no White pawn on this file
        int whiteKingX = 0, whiteKingY = 0, blackKingX = 0, blackKingY = 0; // king squares
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (piece instanceof Pawn) {
                    if (piece.getSide() == Faction.WHITE) {
                        if (y < whiteMinRank[x])
                            whiteMinRank[x] = y;
                    } else {
                        if (y > blackMaxRank[x])
                            blackMaxRank[x] = y;
                    }
                } else if (piece instanceof King) {
                    if (piece.getSide() == Faction.WHITE) {
                        whiteKingX = x;
                        whiteKingY = y;
                    } else {
                        blackKingX = x;
                        blackKingY = y;
                    }
                }
            }
        }

        int endgameWeight = PHASE_MAX - phase; // 0 in the opening, PHASE_MAX in a bare endgame
        int score = 0; // from White's perspective
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Piece piece = board[x][y].getContain();
                if (!(piece instanceof Pawn)) {
                    continue; // only pawns can be passed
                }
                boolean white = piece.getSide() == Faction.WHITE;
                if (!isPassedPawn(x, y, white, blackMaxRank, whiteMinRank)) {
                    continue; // a pawn an enemy pawn can still stop earns nothing here
                }

                int relativeRank = white ? y : 7 - y; // how far advanced, from the owner's side
                int bonus = PASSED_PAWN_BASE[relativeRank]; // the always-on push bonus

                // Endgame extras: a bigger push bonus plus a king-escort term, both
                // fading in as material comes off the board.
                int ownKingDist = white ? chebyshev(whiteKingX, whiteKingY, x, y)
                        : chebyshev(blackKingX, blackKingY, x, y);
                int enemyKingDist = white ? chebyshev(blackKingX, blackKingY, x, y)
                        : chebyshev(whiteKingX, whiteKingY, x, y);
                int escort = (enemyKingDist - ownKingDist) * PASSED_PAWN_KING_ESCORT; // + if our king is closer
                bonus += (PASSED_PAWN_ENDGAME[relativeRank] + escort) * endgameWeight / PHASE_MAX;

                if (white) { // a passer helps its owner
                    score += bonus; // add it for White
                } else {
                    score -= bonus; // subtract it for Black
                }
            }
        }
        return score; // total passed-pawn bonus
    }

    /**
     * Tests whether the pawn on (x, y) is passed: no enemy pawn on its own file or
     * either adjacent file stands ahead of it (between it and promotion). Uses the
     * per-file extremes gathered by {@link #passedPawnScore}.
     */
    private boolean isPassedPawn(int x, int y, boolean white, int[] blackMaxRank, int[] whiteMinRank) {
        for (int f = x - 1; f <= x + 1; f++) { // our file and the two neighbours
            if (f < 0 || f > 7) {
                continue; // off the edge of the board
            }
            if (white) {
                if (blackMaxRank[f] > y) { // a Black pawn still stands ahead of us
                    return false;
                }
            } else {
                if (whiteMinRank[f] < y) { // a White pawn still stands ahead of us
                    return false;
                }
            }
        }
        return true; // nothing in the way -> passed
    }

    /**
     * King-move (Chebyshev) distance between two squares: the number of king steps
     * from (ax, ay) to (bx, by). Used to measure how well a king escorts or catches
     * a passed pawn.
     */
    private int chebyshev(int ax, int ay, int bx, int by) {
        return Math.max(Math.abs(ax - bx), Math.abs(ay - by));
    }

    /**
     * Measures the game phase from the material still on the board, used to blend
     * the king's two piece-square tables and to fade the phase-specific extras.
     *
     * Each non-pawn piece adds a phase weight (knight/bishop = 1, rook = 2,
     * queen = 4). The opening total is {@link #PHASE_MAX} (24); as pieces are
     * traded
     * the total falls toward 0. We clamp to PHASE_MAX so extra queens from
     * promotion
     * can never push it above the maximum.
     *
     * @param board the 8x8 grid of squares to inspect
     * @return the phase count, clamped to the range 0..PHASE_MAX
     */
    public int gamePhase(Cell[][] board) {
        int phase = 0; // running total of phase weights
        for (int x = 0; x < 8; x++) { // every file
            for (int y = 0; y < 8; y++) { // every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece instanceof Knight || piece instanceof Bishop) {
                    phase += 1; // a minor piece is worth 1 phase point
                } else if (piece instanceof Rook) {
                    phase += 2; // a rook is worth 2
                } else if (piece instanceof Queen) {
                    phase += 4; // a queen is worth 4
                }
            }
        }
        return Math.min(phase, PHASE_MAX); // never report more than a full board
    }
}
