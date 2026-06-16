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
 * minimax search. It returns a score in "centipawns" from White's point of view:
 * a positive number means White is better, a negative number means Black is.
 *
 * This class owns everything that does NOT depend on the game phase - raw
 * material, the five non-king piece-square tables, and the game-phase count - and
 * it orchestrates the two phase-specific evaluators:
 *   {@link MiddleGameEvalation} (midgame king table + castling + development) and
 *   {@link EndGameEvaluation} (endgame king table + mop-up).
 * The king's table is "tapered": blended between the two phase tables by how much
 * material is still on the board, and the phase-specific extras fade in and out
 * the same way.
 */
public class MainEvaluation {

    // We work in "centipawns": a pawn is worth 100, so smaller positional bonuses
    // (half a pawn = 50, ...) have room to nudge ties without ever outweighing a
    // whole piece. Multiplying each Piece's value by this keeps that convention.
    private static final int CENTIPAWNS = 100;

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

    // ---- Passed-pawn bonuses (the key to converting a won endgame) ----
    // A pawn is "passed" when no enemy pawn can stop it: none on its own file or
    // either adjacent file stands ahead of it. Indexed by the pawn's RELATIVE rank
    // (how far it has advanced from its own side: 1 = just off the start ... 6 = one
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
            { -10, 5, 0, 0, 0, 0, 5, -10 }, // rank 2 (fianchetto b2/g2)
            { -10, 10, 10, 10, 10, 10, 10, -10 }, // rank 3
            { -10, 0, 10, 10, 10, 10, 0, -10 }, // rank 4
            { -10, 5, 5, 10, 10, 5, 5, -10 }, // rank 5
            { -10, 0, 5, 10, 10, 5, 0, -10 }, // rank 6
            { -10, 0, 0, 0, 0, 0, 0, -10 }, // rank 7
            { -20, -10, -10, -10, -10, -10, -10, -20 }, // rank 8
    };

    // Rook table (centipawns, White's view, [rank][file]). Rooks belong on open
    // files and especially the 7th rank (rank index 6), where they cramp the enemy
    // king; the back-rank centre files get a small nudge for connecting/centralising.
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
     * @param ply   how many half-moves have been played (gates the opening-only terms)
     * @return the position value in centipawns (positive = White is better)
     */
    public int evaluate(Cell[][] board, int ply) {
        int phase = gamePhase(board); // PHASE_MAX = full board, 0 = bare endgame
        int score = pieceRawMaterial(board); // raw material is the same in any phase
        score += pieceSquareScore(board, phase); // positional bonuses, king blended by phase
        score += rookFileScore(board); // reward rooks on open / half-open files
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
                int value = piece.getValue() * CENTIPAWNS; // this piece's worth in centipawns
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
     * Rewards PASSED PAWNS - the single most important endgame term. A pawn with no
     * enemy pawn able to stop it (none on its own file or either adjacent file ahead
     * of it) gets a bonus that climbs steeply as it nears promotion. The bonus has a
     * phase-independent BASE plus an ENDGAME part (a bigger push bonus and a
     * king-escort term) that grows as the board empties - this is what gives the
     * search a clear gradient to march a passer to queen instead of shuffling into a
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
                        if (y < whiteMinRank[x]) whiteMinRank[x] = y;
                    } else {
                        if (y > blackMaxRank[x]) blackMaxRank[x] = y;
                    }
                } else if (piece instanceof King) {
                    if (piece.getSide() == Faction.WHITE) { whiteKingX = x; whiteKingY = y; }
                    else { blackKingX = x; blackKingY = y; }
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
     * queen = 4). The opening total is {@link #PHASE_MAX} (24); as pieces are traded
     * the total falls toward 0. We clamp to PHASE_MAX so extra queens from promotion
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
