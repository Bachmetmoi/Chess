package control; // same package as GameController/LegalMove so the engine sits with the other game logic

import java.util.ArrayList; // a growable list, used to collect the legal moves we generate
import java.util.List; // the List interface type we pass around

import entity.board.Cell; // one square of the board (knows its piece, if any)
import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.move.Move; // the abstract "a move" type (NormalMove, Castling, Promotion, ...)
import entity.move.Promotion; // a pawn-promotion move; we must fill in which piece it becomes
import entity.pieces.Bishop; // bishops get a per-square bonus (piece-square table)
import entity.pieces.King; // needed to locate the king when testing for check
import entity.pieces.Knight; // knights get a per-square bonus (piece-square table)
import entity.pieces.Pawn; // pawns get an extra per-square bonus (piece-square table)
import entity.pieces.Piece; // the abstract "a chess piece" type
import entity.pieces.Queen; // we always promote to a queen in this simple engine
import entity.pieces.Rook; // rooks get a per-square bonus (piece-square table)
import entity.state.GameState; // holds whose turn it is, the board, and the move history

/**
 * A deliberately tiny chess engine for learning.
 *
 * It uses two classic ideas:
 *   1) MINIMAX  - look ahead a fixed number of moves, assuming both sides play
 *                 their best reply, and pick the move that leads to the best
 *                 outcome for the side to move.
 *   2) ALPHA-BETA PRUNING - a speed-up for minimax that skips branches which
 *                 can no longer affect the final decision.
 *
 * To keep the code readable it reuses the project's existing rules: it asks the
 * pieces for their moves, asks {@link LegalMove} whether a move is legal, and
 * uses {@link GameController#executes}/{@link GameController#undoMove} to play
 * and take back moves while searching.
 */
public class ChessEngine {
    // A score so large it stands for "winning"/"losing"; used as the starting
    // best value and as the value of checkmate. Chosen well above any possible
    // material total so a real mate always beats any material count.
    private static final int INFINITY = 1_000_000;

    // Bonus for a king that has castled, in the same units as material (a pawn is
    // worth ~1, see Pawn's value). 50 is about half a pawn: enough that the engine
    // will castle when nothing more valuable is at stake, but not so much that it
    // ignores a real capture to do it. Tune this up to make it castle more eagerly.
    private static final int CASTLE_BONUS = 50;

    // Penalty (centipawns) for a knight or bishop still sitting on its starting
    // square, i.e. not yet developed. This is what stops the engine from shuffling
    // one already-developed knight around the board: moving a piece a SECOND time
    // removes no penalty, but bringing a NEW minor out does, so the bot is nudged to
    // develop all of its pieces before fiddling. It fades out toward the endgame
    // (scaled by game phase), where "development" no longer means anything.
    private static final int UNDEVELOPED_MINOR_PENALTY = 15;

    // Piece-square table for pawns, in centipawns, from White's point of view.
    // Read it as PAWN_PST[y][x]: y is the rank (0 = rank 1, White's back rank;
    // 7 = rank 8) and x is the file (0 = a-file ... 7 = h-file). Black pawns use
    // the same table flipped top-to-bottom (7 - y), which mirrors the values onto
    // Black's side.
    //
    // The values CLIMB STEEPLY with the rank, on purpose: each push up the board is
    // worth clearly more than the square behind it (~+10..+20 per rank), so the
    // search always sees a reason to advance a pawn rather than leave it sitting.
    // Central files are still favoured, and the d/e pawns on their start squares
    // are worth LESS so the engine pushes them out (e2-e4, d2-d4). On the 7th rank
    // a pawn is worth almost a whole extra pawn, on top of the big material jump it
    // gets the moment it promotes.
    private static final int[][] PAWN_PST = {
            { 0, 0, 0, 0, 0, 0, 0, 0 }, // rank 1 (pawns never stand here)
            { 5, 10, 10, -20, -20, 10, 10, 5 }, // rank 2 (start): d2/e2 discouraged
            { 5, 5, 10, 15, 15, 10, 5, 5 }, // rank 3
            { 10, 10, 20, 30, 30, 20, 10, 10 }, // rank 4: d4/e4 rewarded
            { 20, 20, 30, 40, 40, 30, 20, 20 }, // rank 5
            { 40, 40, 50, 60, 60, 50, 40, 40 }, // rank 6: well advanced, push it
            { 80, 80, 80, 90, 90, 80, 80, 80 }, // rank 7 (about to promote)
            { 0, 0, 0, 0, 0, 0, 0, 0 }, // rank 8 (promotion handled by material)
    };

    // Knight table (centipawns, White's view, [rank][file]). Standard "Simplified
    // Evaluation" values: knights love the centre (+20) and hate the rim/corners
    // ("a knight on the rim is dim"). Capped at ±50 so it never rivals material:
    // a centralised knight is worth 300 + 20, not 300 + 50. Symmetric every way.
    private static final int[][] KNIGHT_PST = {
        { -50, -40, -30, -30, -30, -30, -40, -50 }, // rank 1
        { -40, -20,   0,   5,   5,   0, -20, -40 }, // rank 2
        { -30,   5,  10,  15,  15,  10,   5, -30 }, // rank 3
        { -30,   0,  15,  20,  20,  15,   0, -30 }, // rank 4
        { -30,   5,  15,  20,  20,  15,   5, -30 }, // rank 5
        { -30,   0,  10,  15,  15,  10,   0, -30 }, // rank 6
        { -40, -20,   0,   0,   0,   0, -20, -40 }, // rank 7
        { -50, -40, -30, -30, -30, -30, -40, -50 }, // rank 8
    };

    // Bishop table (centipawns, White's view, [rank][file]). Standard values:
    // bishops want long open diagonals and the centre (+10), with a small reward
    // for the fianchetto squares b2/g2 (rank 2) and a penalty for the edges.
    private static final int[][] BISHOP_PST = {
        { -20, -10, -10, -10, -10, -10, -10, -20 }, // rank 1
        { -10,   5,   0,   0,   0,   0,   5, -10 }, // rank 2 (fianchetto b2/g2)
        { -10,  10,  10,  10,  10,  10,  10, -10 }, // rank 3
        { -10,   0,  10,  10,  10,  10,   0, -10 }, // rank 4
        { -10,   5,   5,  10,  10,   5,   5, -10 }, // rank 5
        { -10,   0,   5,  10,  10,   5,   0, -10 }, // rank 6
        { -10,   0,   0,   0,   0,   0,   0, -10 }, // rank 7
        { -20, -10, -10, -10, -10, -10, -10, -20 }, // rank 8
    };
    private static final int[][] ROOK_PST = {
        { 0, -10, 10, 10, 10, 10, -10, 0}, // rank 1
        { -10, 0, 0, 0, 0, 0, 0, -10}, // rank 2
        { 5, 0, 0, 0, 0, 0, 0, 5}, // rank 3
        { 5, 10, 20, 30, 30, 20, 10, 5}, // rank 4
        { 5, 10, 20, 30, 30, 20, 10, 5}, // rank 5
        { 5, 10, 20, 30, 30, 20, 10, 5}, // rank 6
        { 35, 40, 35, 45, 45, 35, 40, 35}, // rank 7
        {30 , 30, 30, 35, 35, 30,30, 30}, // rank 8
        
    };
    private static final int[][] QUEEN_PST = {
        { 0, 0, 0, 0, 0, 0, 0, 0}, // rank 1
        { 0, 0, 0, 0, 0, 0, 0, 0}, // rank 2
        { 0, 0, 0, 0, 0, 0, 0, 0}, // rank 3
        { 0, 0, 0, 0, 0, 0, 0, 0}, // rank 4
        { 0, 0, 0, 0, 0, 0, 0, 0}, // rank 5
        { 5, 10, 20, 30, 30, 20, 10, 5}, // rank 6
        { 35, 40, 35, 45, 45, 35, 40, 35}, // rank 7
        {30 , 30, 30, 35, 35, 30,30, 30}, // rank 8

    };

    // ---- FIX 2: two king tables, one per game phase (a "tapered" evaluation) ----
    // A single king table can't be right all game: early on the king wants to hide
    // on the back rank behind its pawns, but in the endgame (few pieces left) it
    // should march to the CENTRE and help. So we keep two tables and blend between
    // them based on how much material is still on the board (see gamePhase()).

    // MIDGAME king table: reward staying tucked on the back rank (especially the
    // castled g/b squares) and PUNISH wandering up the board into danger.
    private static final int[][] KING_PST_MIDGAME = {
        {  20,  30,  10,   0,   0,  10,  30,  20}, // rank 1 (home): castled squares best
        {  20,  20,   0,   0,   0,   0,  20,  20}, // rank 2
        { -10, -20, -20, -20, -20, -20, -20, -10}, // rank 3
        { -20, -30, -30, -40, -40, -30, -30, -20}, // rank 4
        { -30, -40, -40, -50, -50, -40, -40, -30}, // rank 5
        { -30, -40, -40, -50, -50, -40, -40, -30}, // rank 6
        { -30, -40, -40, -50, -50, -40, -40, -30}, // rank 7
        { -30, -40, -40, -50, -50, -40, -40, -30}, // rank 8
    };

    // ENDGAME king table: now the centre is BEST and the edges/corners are worst,
    // so the king is rewarded for stepping forward to support pawns and help mate.
    private static final int[][] KING_PST_ENDGAME = {
        { -50, -30, -30, -30, -30, -30, -30, -50}, // rank 1
        { -30, -10,   0,   0,   0,   0, -10, -30}, // rank 2
        { -30,   0,  20,  30,  30,  20,   0, -30}, // rank 3
        { -30,   0,  30,  40,  40,  30,   0, -30}, // rank 4
        { -30,   0,  30,  40,  40,  30,   0, -30}, // rank 5
        { -30,   0,  20,  30,  30,  20,   0, -30}, // rank 6
        { -30, -10,   0,   0,   0,   0, -10, -30}, // rank 7
        { -50, -30, -30, -30, -30, -30, -30, -50}, // rank 8
    };

    // Game-phase accounting for the tapered king table above. Each non-pawn piece
    // contributes a "phase weight"; summed over the whole board this measures how
    // far from a bare-bones endgame we are. At the start it totals PHASE_MAX (24):
    // per side 2 knights + 2 bishops (1 each) + 2 rooks (2 each) + 1 queen (4) = 12,
    // times two sides = 24. As pieces come off, the total falls toward 0 (endgame).
    private static final int PHASE_MAX = 24;

    // ---- FIX 1: "mop-up" evaluation to actually FORCE checkmate when winning ----
    // Once a side is well ahead in a simplified position, plain material makes every
    // move look equally winning, so the engine just shuffles and never mates. These
    // constants drive a gradient that (a) pushes the LOSING king to a corner and
    // (b) walks the WINNING king up to help. They are deliberately small (tens of
    // centipawns) so they only break ties between already-winning moves.
    private static final int ENDGAME_PHASE = 6;     // only mop up once phase has fallen this low
    private static final int MOP_UP_MIN_LEAD = 400; // and only when ahead by ~4 pawns of material

    private final GameController gameController; // the game we are thinking about (board + rules live here)
    private final int searchDepth; // how many plies (half-moves) deep we look ahead

    /**
     * @param gameController the controller holding the board we want to analyse
     * @param searchDepth    how many half-moves to look ahead (3 or 4 is fine to start)
     */
    public ChessEngine(GameController gameController, int searchDepth) {
        this.gameController = gameController; // remember the controller so we can read the board and play moves
        this.searchDepth = searchDepth; // remember how deep the search should go
    }

    /**
     * The public entry point: returns the move the engine thinks is best for
     * whichever side is to move right now, or {@code null} if there are none.
     */
    public Move findBestMove() {
        GameState state = gameController.getGameState(); // grab the current game state (turn, board, history)
        boolean whiteToMove = state.getTurn() == Faction.WHITE; // White wants the HIGHEST score, Black the LOWEST

        List<Move> legalMoves = generateLegalMoves(); // every legal move for the side to move right now
        if (legalMoves.isEmpty()) { // no moves at all means the game is already over (mate or stalemate)
            return null; // nothing to choose, so report "no move"
        }

        Move bestMove = null; // the best move found so far (none yet)
        int bestScore = whiteToMove ? -INFINITY : INFINITY; // start at the worst possible score for our side
        int alpha = -INFINITY; // alpha = best score the maximiser (White) can already guarantee
        int beta = INFINITY; // beta  = best score the minimiser (Black) can already guarantee

        for (Move move : legalMoves) { // try each candidate move one at a time
            gameController.executes(move); // play the move on the real board (this also flips the turn)
            // Score this move by looking ahead. After our move it is the OTHER
            // side's turn, so the recursion's "maximising" flag flips.
            int score = minimax(searchDepth - 1, alpha, beta, !whiteToMove);
            gameController.undoMove(); // take the move back so the board is unchanged for the next candidate

            if (whiteToMove) { // White is the maximiser: keep the move with the HIGHEST score
                if (score > bestScore) { // found a better move for White?
                    bestScore = score; // remember its score
                    bestMove = move; // remember the move itself
                }
                alpha = Math.max(alpha, bestScore); // White can now guarantee at least this much
            } else { // Black is the minimiser: keep the move with the LOWEST score
                if (score < bestScore) { // found a better (lower) move for Black?
                    bestScore = score; // remember its score
                    bestMove = move; // remember the move itself
                }
                beta = Math.min(beta, bestScore); // Black can now guarantee at most this much
            }
        }

        return bestMove; // hand back the best move we found
    }

    /**
     * The recursive heart of the engine.
     *
     * @param depth      how many more plies to look ahead (0 = stop and evaluate)
     * @param alpha      best score the maximiser can already force from above
     * @param beta       best score the minimiser can already force from above
     * @param maximizing true when it is the maximiser's (White's) turn at this node
     * @return the position's value, in centipawn-like units, from White's point of view
     */
    private int minimax(int depth, int alpha, int beta, boolean maximizing) {
        List<Move> legalMoves = generateLegalMoves(); // all legal replies for whoever is to move here

        if (legalMoves.isEmpty()) { // no legal moves: the game ends at this node
            Faction sideToMove = gameController.getGameState().getTurn(); // whose turn just ran out of moves
            if (isInCheck(sideToMove)) { // king attacked AND no moves = CHECKMATE
                // The side to move has been mated. If that side is White the
                // result is terrible for White (very negative); if Black, great
                // for White (very positive). We nudge by depth so the engine
                // prefers mating sooner (fewer pieces left to search).
                return maximizing ? -INFINITY - depth : INFINITY + depth;
            }
            return 0; // not in check but no moves = STALEMATE = a draw = score 0
        }

        if (depth == 0) { // reached the lookahead limit: stop recursing on quiet play
            // But don't evaluate blindly in the middle of a capture sequence (that
            // causes the "horizon effect" where the bot misses or misjudges
            // captures). Instead run a quiescence search that keeps resolving
            // captures until the position is calm, THEN scores it.
            return quiescence(alpha, beta, maximizing);
        }

        if (maximizing) { // White to move: try to MAXIMISE the score
            int best = -INFINITY; // start from the worst possible value for the maximiser
            for (Move move : legalMoves) { // examine each legal move
                gameController.executes(move); // play it (also flips the turn)
                int score = minimax(depth - 1, alpha, beta, false); // recurse; now it's the minimiser's turn
                gameController.undoMove(); // undo so siblings start from the same position
                best = Math.max(best, score); // keep the highest score seen so far
                alpha = Math.max(alpha, best); // raise the maximiser's guaranteed floor
                if (beta <= alpha) { // BETA CUT-OFF: the minimiser already has a better option elsewhere,
                    break; // so it would never let us reach this branch -> stop searching it
                }
            }
            return best; // the best score the maximiser can force from this position
        } else { // Black to move: try to MINIMISE the score
            int best = INFINITY; // start from the worst possible value for the minimiser
            for (Move move : legalMoves) { // examine each legal move
                gameController.executes(move); // play it (also flips the turn)
                int score = minimax(depth - 1, alpha, beta, true); // recurse; now it's the maximiser's turn
                gameController.undoMove(); // undo so siblings start from the same position
                best = Math.min(best, score); // keep the lowest score seen so far
                beta = Math.min(beta, best); // lower the minimiser's guaranteed ceiling
                if (beta <= alpha) { // ALPHA CUT-OFF: the maximiser already has a better option elsewhere,
                    break; // so it would never let us reach this branch -> stop searching it
                }
            }
            return best; // the best score the minimiser can force from this position
        }
    }

    /**
     * QUIESCENCE SEARCH - the cure for the "horizon effect".
     *
     * Plain minimax stops after a fixed number of plies and scores whatever it
     * finds, even if a capture is half-finished. That makes the bot misjudge
     * captures: it grabs a piece that gets recaptured just past the horizon, or
     * shies away from a winning capture whose follow-up it can't yet see.
     *
     * This method fixes that by, at the leaves, looking ONLY at captures and
     * playing them out until no captures remain (the position is "quiet"), then
     * returning the calm score. Because every capture removes a piece, the board
     * empties and the recursion always terminates.
     *
     * It uses "stand-pat": the side to move may simply decline to capture, so the
     * static score is a floor for the maximiser (a ceiling for the minimiser). That
     * stops the search from forcing bad captures just because they exist.
     *
     * Simplifications for this learning engine: it only follows ordinary captures
     * (en-passant and promotions are left to the main search) and treats being in
     * check like any other position.
     *
     * @param alpha      best score the maximiser can already force from above
     * @param beta       best score the minimiser can already force from above
     * @param maximizing true when it is the maximiser's (White's) turn here
     * @return the quiet position value, from White's point of view
     */
    private int quiescence(int alpha, int beta, boolean maximizing) {
        int standPat = evaluate(); // the score if the side to move makes no capture at all
        List<Move> captures = generateCaptureMoves(); // only the capturing moves available here

        if (maximizing) { // White to move: maximise
            int best = standPat; // we can always choose to stop here (stand pat)
            alpha = Math.max(alpha, best); // raise the floor with the stand-pat score
            if (alpha >= beta) { // already good enough that the minimiser avoids this line
                return best; // prune
            }
            for (Move move : captures) { // try each capture
                gameController.executes(move); // play it (flips the turn)
                int score = quiescence(alpha, beta, false); // keep resolving captures for the other side
                gameController.undoMove(); // take it back
                best = Math.max(best, score); // keep the best capture sequence
                alpha = Math.max(alpha, best); // raise the floor
                if (alpha >= beta) { // beta cut-off
                    break; // stop searching further captures
                }
            }
            return best; // best the maximiser can get once things are quiet
        } else { // Black to move: minimise
            int best = standPat; // Black, too, may decline to capture
            beta = Math.min(beta, best); // lower the ceiling with the stand-pat score
            if (alpha >= beta) { // already low enough that the maximiser avoids this line
                return best; // prune
            }
            for (Move move : captures) { // try each capture
                gameController.executes(move); // play it (flips the turn)
                int score = quiescence(alpha, beta, true); // keep resolving captures for the other side
                gameController.undoMove(); // take it back
                best = Math.min(best, score); // keep the best (lowest) capture sequence
                beta = Math.min(beta, best); // lower the ceiling
                if (alpha >= beta) { // alpha cut-off
                    break; // stop searching further captures
                }
            }
            return best; // best the minimiser can get once things are quiet
        }
    }

    /**
     * Builds only the LEGAL CAPTURING moves for the side to move, used by the
     * quiescence search. A move is a capture when its destination square already
     * holds an enemy piece. (En-passant lands on an empty square, so it is not
     * detected here - a deliberate simplification.)
     */
    private List<Move> generateCaptureMoves() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to read targets from
        Faction sideToMove = gameController.getGameState().getTurn(); // whose captures we want
        List<Move> captures = new ArrayList<>(); // collected capturing moves
        for (Move move : generateLegalMoves()) { // start from the fully legal move list
            Piece target = board[move.getEndXPos()][move.getEndYPos()].getContain(); // what sits on the target square
            if (target != null && target.getSide() != sideToMove) { // an enemy piece there => this move captures
                captures.add(move); // keep it
            }
        }
        return captures; // just the captures
    }

    /**
     * A very simple position score: just add up material, counting White pieces
     * as positive and Black pieces as negative. So a positive number means White
     * is ahead and a negative number means Black is ahead.
     *
     * Real engines also weigh piece activity, pawn structure, etc. We keep just
     * material plus a small king-safety term (a bonus for having castled).
     *
     * Scores are in "centipawns": we multiply each piece value by 100 so a pawn
     * is worth 100. That gives small bonuses like {@link #CASTLE_BONUS} (50 = half
     * a pawn) room to nudge ties without ever outweighing real material.
     */
    private int evaluate() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the 8x8 grid of squares
        int score = 0; // running total, from White's perspective
        int phase = gamePhase(); // FIX 2: how "middlegame" we still are (PHASE_MAX = full, 0 = bare endgame)
        for (int x = 0; x < 8; x++) { // walk every file (column)
            for (int y = 0; y < 8; y++) { // walk every rank (row)
                Piece piece = board[x][y].getContain(); // the piece on this square, or null if empty
                if (piece == null) { // empty square contributes nothing
                    continue; // skip to the next square
                }
                int bonus = pieceSquareBonus(piece, x, y, phase); // per-square positional bonus (king uses phase)
                if (piece.getSide() == Faction.WHITE) { // a White piece helps White
                    score += piece.getValue() * 100; // so add its value (×100 -> centipawns)
                    score += bonus; // plus its piece-square bonus (already in White's orientation)
                } else { // a Black piece helps Black
                    score -= piece.getValue() * 100; // so subtract its value (×100 -> centipawns)
                    score -= bonus; // minus its piece-square bonus (already mirrored onto Black)
                }
            }
        }

        // King safety: reward each side for having castled. A White castle pushes
        // the score up (good for White); a Black castle pushes it down.
        if (kingHasCastled(Faction.WHITE)) { // has White's king castled?
            score += CASTLE_BONUS; // reward White
        }
        if (kingHasCastled(Faction.BLACK)) { // has Black's king castled?
            score -= CASTLE_BONUS; // reward Black
        }

        score += developmentScore(phase); // reward getting minor pieces off their home squares
        score += mopUpScore(phase); // FIX 1: in a winning endgame, nudge toward forcing mate

        return score; // material + king-safety + development + (in endgames) the mate gradient
    }

    /**
     * Rewards DEVELOPMENT: every knight or bishop still on its starting square
     * (it has never moved) is a small penalty for its owner. This is what fixes the
     * "keeps pushing the same knight" behaviour - once a minor has moved, moving it
     * again earns nothing here, but bringing out a NEW minor removes another
     * penalty, so the engine prefers to develop the rest of its army first.
     *
     * The whole term is scaled by the game phase so it matters in the opening and
     * fades to nothing in the endgame, where "undeveloped" is meaningless.
     *
     * @param phase the current game phase (PHASE_MAX = opening, 0 = bare endgame)
     * @return a centipawn nudge from White's point of view
     */
    private int developmentScore(int phase) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to inspect
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
        return penalty * phase / PHASE_MAX; // full weight in the opening, fading toward the endgame
    }

    /**
     * Looks up the piece-square bonus (in centipawns) for a piece sitting on the
     * square (x, y), where x is the file and y is the rank.
     *
     * Each piece type has its own table laid out from White's point of view, just
     * like {@link #PAWN_PST}. White reads the table directly with the rank y; Black
     * reads it flipped top-to-bottom (7 - y), which mirrors White's values onto
     * Black's side of the board. The king has no table, so it scores 0 here.
     *
     * The returned value is always "good for the owner": positive means the square
     * is a good one for that piece. {@link #evaluate} adds it for White and
     * subtracts it for Black.
     *
     * @param phase the current game phase (PHASE_MAX = full middlegame, 0 = bare
     *              endgame); only the king uses it, to blend its two tables.
     */
    private int pieceSquareBonus(Piece piece, int x, int y, int phase) {
        int rank = (piece.getSide() == Faction.WHITE) ? y : 7 - y; // flip the rank for Black

        if (piece instanceof King) { // FIX 2: the king's bonus depends on the game phase
            int mid = KING_PST_MIDGAME[rank][x]; // value if it were still a middlegame
            int end = KING_PST_ENDGAME[rank][x]; // value in a pure endgame
            // Blend the two by how much material is left: at PHASE_MAX we use the
            // midgame value, at 0 the endgame value, smoothly in between.
            return (mid * phase + end * (PHASE_MAX - phase)) / PHASE_MAX;
        }

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
        return table[rank][x]; // read the bonus for this square, in White's orientation
    }

    /**
     * Measures the game phase from the material still on the board, used to blend
     * the king's two piece-square tables (see {@link #pieceSquareBonus}).
     *
     * Each non-pawn piece adds a "phase weight" (knight/bishop = 1, rook = 2,
     * queen = 4). The opening total is {@link #PHASE_MAX} (24); as pieces are
     * traded the total falls toward 0. We clamp to PHASE_MAX so extra queens from
     * promotion can never push it above the maximum.
     */
    private int gamePhase() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to inspect
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

    /**
     * FIX 1: the "mop-up" evaluation that lets a winning side actually force mate.
     *
     * Plain material gives no reason to prefer one winning move over another, so
     * the engine shuffles instead of mating. This term, active only in a clearly
     * won endgame, adds a small gradient that:
     *   - pushes the LOSING king toward a corner (its distance from the centre), and
     *   - walks the WINNING king closer to it (to deliver the mate).
     * The result is returned from White's point of view: positive helps White.
     *
     * @param phase the current game phase; we only mop up once it is low enough
     * @return a centipawn nudge toward mating, or 0 when it does not apply
     */
    private int mopUpScore(int phase) {
        if (phase > ENDGAME_PHASE) { // still too many pieces around: no mop-up yet
            return 0;
        }

        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to inspect
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
                    whiteMaterial += piece.getValue() * 100; // tally White's material
                } else {
                    blackMaterial += piece.getValue() * 100; // tally Black's material
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

    /**
     * Detects whether the given side has castled. We use a simple signal: castling
     * is the only normal way a king lands on its short-castle square (g-file, x=6)
     * or long-castle square (c-file, x=2) on its home rank having already moved.
     */
    private boolean kingHasCastled(Faction side) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to inspect
        int homeRank = (side == Faction.WHITE) ? 0 : 7; // White's back rank is y=0, Black's is y=7
        for (int x : new int[] { 2, 6 }) { // c-file (long castle) and g-file (short castle)
            Piece piece = board[x][homeRank].getContain(); // what sits on that castled square
            if (piece instanceof King && piece.getSide() == side && piece.getMoveCount() > 0) { // our king, and it has moved
                return true; // a moved king on a castle square means it castled
            }
        }
        return false; // king is not on a castled square (or hasn't moved) -> not castled
    }

    /**
     * Builds every LEGAL move for the side whose turn it currently is.
     *
     * It does this in two steps, matching how the rest of the project works:
     *   1) ask each of our pieces for its "pseudo-legal" moves (moves that follow
     *      the piece's movement rules but might leave our own king in check), then
     *   2) keep only the ones {@link LegalMove#isLegal} accepts (king stays safe).
     */
    private List<Move> generateLegalMoves() {
        GameState state = gameController.getGameState(); // current turn, board and history
        Cell[][] board = state.getChessBoard().getBoard(); // the 8x8 grid
        Faction sideToMove = state.getTurn(); // the colour we are generating moves for
        List<Move> history = state.getMoveHistory(); // needed so pawns can detect en passant
        LegalMove legalMove = gameController.getLegalMove(); // the rules object that filters out illegal moves

        List<Move> legalMoves = new ArrayList<>(); // where we collect the moves that survive filtering

        for (int x = 0; x < 8; x++) { // scan every file (column)
            for (int y = 0; y < 8; y++) { // scan every rank (row)
                Piece piece = board[x][y].getContain(); // the piece on this square, if any
                if (piece == null || piece.getSide() != sideToMove) { // skip empty squares and enemy pieces
                    continue; // only our own pieces can move
                }
                for (Move move : piece.move(board, x, y, history)) { // ask the piece for its candidate moves
                    if (move instanceof Promotion) { // pawn reaching the last rank: the generator left the
                        ((Promotion) move).setPiecePromoted(new Queen(sideToMove)); // promoted piece blank, so default it to a queen
                    }
                    if (legalMove.isLegal(move)) { // does this move leave our king safe (i.e. is it truly legal)?
                        legalMoves.add(move); // if so, keep it
                    }
                }
            }
        }
        return legalMoves; // the full list of legal moves for the side to move
    }

    /**
     * Returns true if the given side's king is currently under attack.
     *
     * We find the king, then look at every enemy piece's moves: if any of them
     * lands on the king's square, the king is in check. (This mirrors the logic
     * inside {@link LegalMove}, re-implemented here so the engine can tell
     * checkmate apart from stalemate.)
     */
    private boolean isInCheck(Faction side) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the board to inspect

        int kingX = -1; // file of our king (start with "not found")
        int kingY = -1; // rank of our king (start with "not found")
        for (int x = 0; x < 8; x++) { // search every file
            for (int y = 0; y < 8; y++) { // search every rank
                Piece piece = board[x][y].getContain(); // piece on this square
                if (piece instanceof King && piece.getSide() == side) { // our king?
                    kingX = x; // remember its file
                    kingY = y; // remember its rank
                }
            }
        }

        for (int x = 0; x < 8; x++) { // now scan the board again for enemy pieces
            for (int y = 0; y < 8; y++) { // every square
                Piece piece = board[x][y].getContain(); // piece on this square
                if (piece == null || piece.getSide() == side) { // ignore empty squares and our own pieces
                    continue; // only enemy pieces can give check
                }
                for (Move move : piece.move(board, x, y)) { // each square this enemy piece attacks
                    if (move.getEndXPos() == kingX && move.getEndYPos() == kingY) { // does it hit our king?
                        return true; // yes -> our king is in check
                    }
                }
            }
        }
        return false; // no enemy move reaches the king -> not in check
    }
}
