package control; // same package as GameController/LegalMove so the engine sits with the other game logic

import java.util.ArrayList; // a growable list, used to collect the legal moves we generate
import java.util.Arrays; // used to clear the history table at the start of each search
import java.util.List; // the List interface type we pass around

import control.evaluate.MainEvaluation; // scores a leaf position (material + positional terms)
import entity.board.Cell; // one square of the board (knows its piece, if any)
import entity.enums.Faction; // the colour enum: WHITE or BLACK
import entity.move.Move; // the abstract "a move" type (NormalMove, Castling, Promotion, ...)
import entity.move.Promotion; // a pawn-promotion move; we must fill in which piece it becomes
import entity.pieces.King; // needed to locate the king when testing for check
import entity.pieces.Piece; // the abstract "a chess piece" type
import entity.pieces.Queen; // we always promote to a queen in this simple engine
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

    // How many extra plies the quiescence search may chase captures past the main
    // search horizon. Real capture sequences rarely run longer than this, so the
    // cap almost never changes the chosen move - it just stops a pathological line
    // (or the per-node move-generation cost) from blowing up the search time.
    private static final int QUIESCENCE_MAX_PLY = 8;

    // Scores a quiet leaf position (material + piece-square tables + king safety +
    // mop-up). All the evaluation knowledge now lives in the control.evaluate
    // package; the engine just asks this object for a number.
    private final MainEvaluation evaluation = new MainEvaluation();

    // ---- Search deeper in the endgame (where it matters and is cheap) ----
    // Endgames have few pieces and so few legal moves: the branching factor is tiny,
    // so we can afford to look MUCH further ahead - which is exactly what is needed
    // to see a promotion several moves away and actually convert a won ending. We
    // read the game phase (PHASE_MAX = full board, 0 = bare endgame) and add extra
    // plies once it drops low enough. The thresholds mirror the ones the evaluator
    // uses for its endgame terms.
    private static final int ENDGAME_PHASE = 6;          // "an endgame": rooks/minors and pawns
    private static final int LATE_ENDGAME_PHASE = 3;     // "a bare endgame": almost no pieces
    private static final int ENDGAME_DEPTH_BONUS = 2;    // extra plies once in an endgame
    private static final int LATE_ENDGAME_DEPTH_BONUS = 4; // extra plies in a bare endgame

    // ---- Time limit for the (deep) endgame search ----
    // The endgame depth bonuses above can make a single fixed-depth search run for a
    // long time on a crowded ending. To keep the engine responsive we cap the endgame
    // search at this wall-clock budget and search it by ITERATIVE DEEPENING: we look
    // one ply deeper each pass, keep the best move from the last pass that FINISHED in
    // time, and stop as soon as the budget is spent. Only the endgame is timed; the
    // opening/middlegame keeps its old single fixed-depth search.
    private static final long ENDGAME_TIME_LIMIT_NANOS = 15L * 1_000_000_000L; // 15 seconds

    private final GameController gameController; // the game we are thinking about (board + rules live here)
    private final int searchDepth; // how many plies (half-moves) deep we look ahead
    private final boolean useOpeningBook; // the book's lines assume the standard start, so custom games turn it off
    private final OpeningMove openingMove = new OpeningMove(); // picks White's random first move (e4/d4/Nf3/Nc3)

    // Set per call to findBestMove: when timeLimited is true the search must stop once
    // System.nanoTime() reaches deadlineNanos (checked by checkTime, which unwinds the
    // recursion by throwing SearchTimeout). Untimed searches leave timeLimited false.
    private boolean timeLimited; // is the current search subject to the endgame clock?
    private long deadlineNanos; // wall-clock instant (System.nanoTime) at which to abort

    // ---- Move-ordering memory (rebuilt for each findBestMove call) ----
    // Good move ordering is what makes alpha-beta prune hard, so beyond MVV-LVA we
    // remember two extra signals about QUIET moves that turned out to refute a branch:
    //   * KILLER MOVES: up to two quiet moves, per search depth, that most recently
    //     caused a beta cut-off at that depth. The same move often refutes sibling
    //     positions too, so we try it early.
    //   * HISTORY HEURISTIC: a [from-square][to-square] tally, bumped whenever a quiet
    //     move causes a cut-off (by depth*depth, so deeper cut-offs count for more).
    //     Quiet moves are then ordered by this score.
    private static final int MAX_KILLER_DEPTH = 64; // plenty: base depth + endgame bonus stays well under this
    private final Move[][] killers = new Move[MAX_KILLER_DEPTH][2]; // killers[depth][0..1]
    private final int[][] history = new int[64][64]; // history[fromSquare][toSquare], squares packed as x*8+y

    /**
     * @param gameController the controller holding the board we want to analyse
     * @param searchDepth    how many half-moves to look ahead (3 or 4 is fine to start)
     */
    public ChessEngine(GameController gameController, int searchDepth) {
        this(gameController, searchDepth, true); // a plain engine game starts from the standard position
    }

    /**
     * @param useOpeningBook whether the hardcoded opening book may answer; pass
     *                       {@code false} for games that do not start from the
     *                       standard position (a customized setup), where a book
     *                       reply that happens to be legal could still be a blunder
     */
    public ChessEngine(GameController gameController, int searchDepth, boolean useOpeningBook) {
        this.gameController = gameController; // remember the controller so we can read the board and play moves
        this.searchDepth = searchDepth; // remember how deep the search should go
        this.useOpeningBook = useOpeningBook; // remember whether the book applies to this game
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

        if (useOpeningBook) { // the book only makes sense from the standard starting position
            Move firstMove = openingMove.chooseFirstMove(state, legalMoves); // White's random opener (e4/d4/Nf3/Nc3)
            if (firstMove != null) { // it is White's first move: play the chosen opening
                return firstMove; // no need to search, any of the four is a sound start
            }
            Move bookMove = openingBookMove(state, legalMoves); // hardcoded opening reply for Black, if one applies here
            if (bookMove != null) { // a book move overrides the search (e.g. answer 1.e4 with 1...e5)
                return bookMove; // play it straight away without thinking
            }
        }

        clearOrderingMemory(); // start each decision with fresh killer/history tables
        orderMoves(legalMoves); // try likely-best moves (captures/promotions) first so alpha-beta prunes hard

        int targetDepth = effectiveSearchDepth(); // deepen the search in the endgame (cheap there, and decisive)
        timeLimited = targetDepth > searchDepth; // the depth bonus only fires in the endgame -> that's when we time it
        deadlineNanos = System.nanoTime() + ENDGAME_TIME_LIMIT_NANOS; // when to stop (only consulted if timeLimited)

        // Untimed (opening/middlegame): one search straight to the target depth, exactly
        // as before. Timed (endgame): ITERATIVE DEEPENING - search depth 1, 2, ... up to
        // the target, keeping the best move from the last depth that finished within the
        // budget, and stop the moment the clock runs out.
        Move bestMove = legalMoves.get(0); // safe fallback (best-ordered move) if even depth 1 can't finish
        int startDepth = timeLimited ? 1 : targetDepth;
        for (int depth = startDepth; depth <= targetDepth; depth++) {
            try {
                Move move = searchRoot(depth, whiteToMove, legalMoves); // full search at this depth
                if (move != null) {
                    bestMove = move; // this depth completed in time, so trust its (deeper) verdict
                }
            } catch (SearchTimeout e) {
                break; // ran out of time mid-depth: keep the best move from the last finished depth
            }
        }
        return bestMove; // hand back the best move we found
    }

    /**
     * One full root search at a fixed {@code depth}: tries every candidate move and
     * returns the best one for the side to move, using the same alpha-beta logic the
     * old single-pass loop used. May throw {@link SearchTimeout} if the endgame clock
     * runs out mid-search; the move played here is always undone first (try/finally),
     * so the board is left untouched however this returns.
     */
    private Move searchRoot(int depth, boolean whiteToMove, List<Move> legalMoves) {
        Move bestMove = null; // the best move found so far at this depth (none yet)
        int bestScore = whiteToMove ? -INFINITY : INFINITY; // start at the worst possible score for our side
        int alpha = -INFINITY; // alpha = best score the maximiser (White) can already guarantee
        int beta = INFINITY; // beta  = best score the minimiser (Black) can already guarantee

        for (Move move : legalMoves) { // try each candidate move one at a time
            gameController.executes(move); // play the move on the real board (this also flips the turn)
            int score;
            try {
                // Score this move by looking ahead. After our move it is the OTHER
                // side's turn, so the recursion's "maximising" flag flips.
                score = minimax(depth - 1, alpha, beta, !whiteToMove);
            } finally {
                gameController.undoMove(); // take the move back even if the search aborts on the clock
            }

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
        return bestMove; // the best move at this depth
    }

    /**
     * Thrown to unwind the recursion the instant the endgame time budget is spent.
     * Carries no stack trace (it is control flow, not an error) so throwing it is
     * cheap; a single shared instance, {@link #TIMEOUT}, is reused for every abort.
     */
    private static final class SearchTimeout extends RuntimeException {
        private SearchTimeout() {
            super(null, null, false, false); // no message, no cause, no suppression, no stack trace
        }
    }

    private static final SearchTimeout TIMEOUT = new SearchTimeout(); // reused on every abort (no allocation)

    /**
     * Aborts the search by throwing {@link #TIMEOUT} once the endgame clock has run
     * out. A no-op for untimed (opening/middlegame) searches. Called at the top of
     * every search node, so the abort happens between nodes - the try/finally blocks
     * around each move then undo everything as the exception unwinds.
     */
    private void checkTime() {
        if (timeLimited && System.nanoTime() >= deadlineNanos) {
            throw TIMEOUT;
        }
    }

    /**
     * A tiny hardcoded "opening book": when the position matches a known opening we
     * return the prepared reply instead of searching. Coordinates are board[x][y]
     * with x = file (a=0 .. h=7) and y = rank (rank 1 = 0 .. rank 8 = 7).
     *
     * We play Black and follow one short line, matched against the exact move history:
     *   1.e4 (e2-e4)            -> 1...e5  (e7-e5)
     *   1.e4 e5 2.Nf3 (g1-f3)   -> 2...Nc6 (b8-c6)
     * Each rule fires only when every preceding move matches, so the engine never
     * blunders into a book reply that does not fit the position actually on the board.
     *
     * @return the book move to play, or {@code null} if no rule applies here
     */
    private Move openingBookMove(GameState state, List<Move> legalMoves) {
        if (state.getTurn() != Faction.BLACK) { // the book only covers Black's replies
            return null;
        }
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

    /**
     * The depth to actually search from the current position: the configured
     * {@link #searchDepth} in the opening/middlegame, plus extra plies once the game
     * has simplified into an endgame. Endgames have far fewer legal moves, so the
     * deeper search costs little but lets the engine see promotions and king marches
     * that a shallow search would miss - the difference between drawing and winning a
     * won ending. The phase is read once, here at the root, for the whole search.
     */
    private int effectiveSearchDepth() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the current position
        int phase = evaluation.gamePhase(board); // PHASE_MAX = full board, 0 = bare endgame
        if (phase <= LATE_ENDGAME_PHASE) { // almost no pieces: go a lot deeper
            return searchDepth + LATE_ENDGAME_DEPTH_BONUS;
        }
        if (phase <= ENDGAME_PHASE) { // a normal endgame: go somewhat deeper
            return searchDepth + ENDGAME_DEPTH_BONUS;
        }
        return searchDepth; // opening / middlegame: as configured
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
        checkTime(); // bail out (by throwing) if the endgame time budget is spent
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

        if (gameController.isDrawByRuleForSearch()) { // repetition or fifty-move rule
            // This line has reached a position the rules call a draw, so it is worth
            // exactly 0 to both sides. Returning it here is what stops the engine
            // shuffling into a repetition when winning (a draw beats a loss but loses
            // to a win, so it will only accept it when nothing better exists).
            return 0;
        }

        if (depth == 0) { // reached the lookahead limit: stop recursing on quiet play
            // But don't evaluate blindly in the middle of a capture sequence (that
            // causes the "horizon effect" where the bot misses or misjudges
            // captures). Instead run a quiescence search that keeps resolving
            // captures until the position is calm, THEN scores it.
            return quiescence(alpha, beta, maximizing, QUIESCENCE_MAX_PLY);
        }

        orderMoves(legalMoves, depth); // search promising moves first to maximise alpha-beta cut-offs

        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // to tell captures from quiet moves
        if (maximizing) { // White to move: try to MAXIMISE the score
            int best = -INFINITY; // start from the worst possible value for the maximiser
            for (Move move : legalMoves) { // examine each legal move
                boolean quiet = isQuietMove(board, move); // remember before playing it (board changes after)
                gameController.executes(move); // play it (also flips the turn)
                int score;
                try {
                    score = minimax(depth - 1, alpha, beta, false); // recurse; now it's the minimiser's turn
                } finally {
                    gameController.undoMove(); // undo even if the search aborts on the clock
                }
                best = Math.max(best, score); // keep the highest score seen so far
                alpha = Math.max(alpha, best); // raise the maximiser's guaranteed floor
                if (beta <= alpha) { // BETA CUT-OFF: the minimiser already has a better option elsewhere,
                    if (quiet) {
                        recordCutoff(move, depth); // a quiet move that refutes this branch: remember it for ordering
                    }
                    break; // so it would never let us reach this branch -> stop searching it
                }
            }
            return best; // the best score the maximiser can force from this position
        } else { // Black to move: try to MINIMISE the score
            int best = INFINITY; // start from the worst possible value for the minimiser
            for (Move move : legalMoves) { // examine each legal move
                boolean quiet = isQuietMove(board, move); // remember before playing it (board changes after)
                gameController.executes(move); // play it (also flips the turn)
                int score;
                try {
                    score = minimax(depth - 1, alpha, beta, true); // recurse; now it's the maximiser's turn
                } finally {
                    gameController.undoMove(); // undo even if the search aborts on the clock
                }
                best = Math.min(best, score); // keep the lowest score seen so far
                beta = Math.min(beta, best); // lower the minimiser's guaranteed ceiling
                if (beta <= alpha) { // ALPHA CUT-OFF: the maximiser already has a better option elsewhere,
                    if (quiet) {
                        recordCutoff(move, depth); // a quiet move that refutes this branch: remember it for ordering
                    }
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
    private int quiescence(int alpha, int beta, boolean maximizing, int qPly) {
        checkTime(); // bail out (by throwing) if the endgame time budget is spent
        int standPat = evaluate(); // the score if the side to move makes no capture at all

        // Safety net: stop chasing captures past a fixed depth. Without a cap a long
        // capture sequence (and the per-node cost of regenerating moves) can blow up
        // the search time; once we hit the limit we just return the static score.
        if (qPly <= 0) {
            return standPat;
        }

        List<Move> captures = generateCaptureMoves(); // only the capturing moves available here
        orderMoves(captures); // most-valuable-victim captures first, so cut-offs happen sooner

        if (maximizing) { // White to move: maximise
            int best = standPat; // we can always choose to stop here (stand pat)
            alpha = Math.max(alpha, best); // raise the floor with the stand-pat score
            if (alpha >= beta) { // already good enough that the minimiser avoids this line
                return best; // prune
            }
            for (Move move : captures) { // try each capture
                gameController.executes(move); // play it (flips the turn)
                int score;
                try {
                    score = quiescence(alpha, beta, false, qPly - 1); // keep resolving captures for the other side
                } finally {
                    gameController.undoMove(); // take it back even if the search aborts on the clock
                }
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
                int score;
                try {
                    score = quiescence(alpha, beta, true, qPly - 1); // keep resolving captures for the other side
                } finally {
                    gameController.undoMove(); // take it back even if the search aborts on the clock
                }
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
     * Orders moves in place so the search tries the most promising ones first,
     * which is what makes alpha-beta actually prune: cut-offs happen as soon as a
     * refutation is found, so a good first guess skips most of the rest.
     *
     * The heuristic is MVV-LVA (Most Valuable Victim - Least Valuable Attacker):
     * captures come before quiet moves, grabbing a big piece with a small one ranks
     * highest, and promotions get a bonus. Quiet moves keep their generated order.
     */
    private void orderMoves(List<Move> moves) {
        orderMoves(moves, -1); // -1 = no killer table for this depth (root / quiescence)
    }

    /**
     * Orders {@code moves} in place for a node at the given remaining {@code depth},
     * so the killer moves stored for that depth can be tried early. Pass {@code -1}
     * where no depth applies (the root and the quiescence search, which sees only
     * captures anyway).
     */
    private void orderMoves(List<Move> moves, int depth) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard();
        moves.sort((a, b) -> moveScore(board, b, depth) - moveScore(board, a, depth)); // descending: best first
    }

    // Ordering tiers, kept apart so a higher class of move always sorts before a lower
    // one: any capture (CAPTURE_BASE) outranks a promotion, which outranks a killer,
    // which outranks a history-ranked quiet move (history is capped below the killers).
    private static final int CAPTURE_BASE = 10_000;
    private static final int PROMOTION_BONUS = 9_000;
    private static final int KILLER_1_BONUS = 8_000;
    private static final int KILLER_2_BONUS = 7_000;
    private static final int HISTORY_CAP = 6_000; // quiet-move history can never reach the killer tier

    /**
     * A rough "how interesting is this move" score used only for ordering (not for
     * evaluation). Captures rank highest (MVV-LVA: big victim, small attacker), then
     * promotions, then the two killer moves for this depth, then quiet moves by their
     * history score. {@code depth} is the node's remaining depth, or -1 if none.
     */
    private int moveScore(Cell[][] board, Move move, int depth) {
        int score = 0;
        Piece victim = board[move.getEndXPos()][move.getEndYPos()].getContain(); // piece sitting on the target square
        if (victim != null) { // this move is a capture
            // +base so any capture outranks any quiet move; then reward a big victim
            // and a small attacker (×10 keeps victim value dominant over attacker).
            score += CAPTURE_BASE + victim.getValue() * 10 - move.getPiece().getValue();
        }
        if (move instanceof Promotion) { // turning a pawn into a queen is almost always worth looking at early
            score += PROMOTION_BONUS;
        }
        if (score != 0) { // a capture and/or promotion is already ranked above all quiet moves
            return score;
        }

        // Quiet move: prefer this depth's killers, then fall back to the history score.
        if (depth >= 0 && depth < MAX_KILLER_DEPTH) {
            if (sameMove(move, killers[depth][0])) {
                return KILLER_1_BONUS;
            }
            if (sameMove(move, killers[depth][1])) {
                return KILLER_2_BONUS;
            }
        }
        int from = move.getStartXPos() * 8 + move.getStartYPos();
        int to = move.getEndXPos() * 8 + move.getEndYPos();
        return Math.min(history[from][to], HISTORY_CAP); // capped so history never jumps above a killer
    }

    /**
     * Records a quiet move that caused a beta cut-off at {@code depth}, so the search
     * tries it earlier in sibling positions: it becomes this depth's first killer
     * (shifting the old one to the second slot) and its history score is bumped by
     * {@code depth*depth} (deeper cut-offs are stronger evidence the move is good).
     */
    private void recordCutoff(Move move, int depth) {
        if (depth >= 0 && depth < MAX_KILLER_DEPTH) {
            if (!sameMove(move, killers[depth][0])) { // avoid storing the same move in both slots
                killers[depth][1] = killers[depth][0]; // demote the previous killer
                killers[depth][0] = move; // newest cut-off move becomes the primary killer
            }
        }
        int from = move.getStartXPos() * 8 + move.getStartYPos();
        int to = move.getEndXPos() * 8 + move.getEndYPos();
        history[from][to] += depth * depth;
    }

    /**
     * A move is "quiet" when it neither captures nor promotes - exactly the moves the
     * killer/history heuristics track (captures are already ordered well by MVV-LVA).
     * Must be called BEFORE the move is played, while the target square still holds
     * any captured piece.
     */
    private boolean isQuietMove(Cell[][] board, Move move) {
        Piece target = board[move.getEndXPos()][move.getEndYPos()].getContain();
        return target == null && !(move instanceof Promotion);
    }

    /** True if two moves share the same from- and to-square (enough for ordering). */
    private boolean sameMove(Move a, Move b) {
        return b != null
                && a.getStartXPos() == b.getStartXPos() && a.getStartYPos() == b.getStartYPos()
                && a.getEndXPos() == b.getEndXPos() && a.getEndYPos() == b.getEndYPos();
    }

    /** Clears the killer and history tables so a new search starts with no stale hints. */
    private void clearOrderingMemory() {
        for (Move[] depthKillers : killers) {
            depthKillers[0] = null;
            depthKillers[1] = null;
        }
        for (int[] row : history) {
            Arrays.fill(row, 0);
        }
    }

    /**
     * Builds only the LEGAL CAPTURING moves for the side to move, used by the
     * quiescence search. A move is a capture when its destination square already
     * holds an enemy piece. (En-passant lands on an empty square, so it is not
     * detected here - a deliberate simplification.)
     */
    private List<Move> generateCaptureMoves() {
        GameState state = gameController.getGameState();
        Cell[][] board = state.getChessBoard().getBoard(); // the board to read targets from
        Faction sideToMove = state.getTurn(); // whose captures we want
        List<Move> history = state.getMoveHistory(); // needed so pawns can detect en passant
        LegalMove legalMove = gameController.getLegalMove(); // the rules object that filters out illegal moves
        int[] king = legalMove.findKing(sideToMove); // locate our king ONCE for the legality checks below

        List<Move> captures = new ArrayList<>(); // collected capturing moves
        for (int x = 0; x < 8; x++) { // scan every file
            for (int y = 0; y < 8; y++) { // scan every rank
                Piece piece = board[x][y].getContain(); // piece on this square, if any
                if (piece == null || piece.getSide() != sideToMove) { // only our own pieces can move
                    continue;
                }
                for (Move move : piece.move(board, x, y, history)) { // this piece's candidate moves
                    Piece target = board[move.getEndXPos()][move.getEndYPos()].getContain(); // what sits on the target
                    if (target == null || target.getSide() == sideToMove) { // empty or friendly => not a capture
                        continue; // skip it WITHOUT paying the legality check (the win over filtering the full list)
                    }
                    if (move instanceof Promotion) { // a capturing promotion: default the new piece to a queen
                        ((Promotion) move).setPiecePromoted(new Queen(sideToMove));
                    }
                    if (legalMove.isLegalWithKing(move, king[0], king[1])) { // legal capture (king stays safe)?
                        captures.add(move); // keep it
                    }
                }
            }
        }
        return captures; // just the legal captures
    }

    /**
     * Scores the current leaf position by delegating to {@link MainEvaluation},
     * which adds up material and the positional terms (piece-square tables, king
     * safety, development and the endgame mop-up). The number is in centipawns from
     * White's point of view: positive means White is better, negative means Black.
     */
    private int evaluate() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard(); // the 8x8 grid of squares
        int ply = gameController.getGameState().getMoveHistory().size(); // half-moves played so far (gates opening terms)
        return evaluation.evaluate(board, ply); // all the scoring knowledge lives in control.evaluate now
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
        int[] king = legalMove.findKing(sideToMove); // locate our king ONCE, then reuse it for every move below

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
                    if (legalMove.isLegalWithKing(move, king[0], king[1])) { // truly legal (our king stays safe)?
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
