package control; // same package as GameController/LegalMove so the engine sits with the other game logic

import java.util.ArrayList; // a growable list, used to collect the legal moves we generate
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
        orderMoves(legalMoves); // try likely-best moves (captures/promotions) first so alpha-beta prunes hard

        int depth = effectiveSearchDepth(); // deepen the search in the endgame (cheap there, and decisive)

        Move bestMove = null; // the best move found so far (none yet)
        int bestScore = whiteToMove ? -INFINITY : INFINITY; // start at the worst possible score for our side
        int alpha = -INFINITY; // alpha = best score the maximiser (White) can already guarantee
        int beta = INFINITY; // beta  = best score the minimiser (Black) can already guarantee

        for (Move move : legalMoves) { // try each candidate move one at a time
            gameController.executes(move); // play the move on the real board (this also flips the turn)
            // Score this move by looking ahead. After our move it is the OTHER
            // side's turn, so the recursion's "maximising" flag flips.
            int score = minimax(depth - 1, alpha, beta, !whiteToMove);
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

        orderMoves(legalMoves); // search promising moves first to maximise alpha-beta cut-offs

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
    private int quiescence(int alpha, int beta, boolean maximizing, int qPly) {
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
                int score = quiescence(alpha, beta, false, qPly - 1); // keep resolving captures for the other side
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
                int score = quiescence(alpha, beta, true, qPly - 1); // keep resolving captures for the other side
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
     * Orders moves in place so the search tries the most promising ones first,
     * which is what makes alpha-beta actually prune: cut-offs happen as soon as a
     * refutation is found, so a good first guess skips most of the rest.
     *
     * The heuristic is MVV-LVA (Most Valuable Victim - Least Valuable Attacker):
     * captures come before quiet moves, grabbing a big piece with a small one ranks
     * highest, and promotions get a bonus. Quiet moves keep their generated order.
     */
    private void orderMoves(List<Move> moves) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard();
        moves.sort((a, b) -> moveScore(board, b) - moveScore(board, a)); // descending: best score first
    }

    /**
     * A rough "how interesting is this move" score used only for ordering (not for
     * evaluation). Capturing a valuable piece with a cheap one scores high; a
     * promotion adds a bonus; everything else scores 0.
     */
    private int moveScore(Cell[][] board, Move move) {
        int score = 0;
        Piece victim = board[move.getEndXPos()][move.getEndYPos()].getContain(); // piece sitting on the target square
        if (victim != null) { // this move is a capture
            // +base so any capture outranks any quiet move; then reward a big victim
            // and a small attacker (×10 keeps victim value dominant over attacker).
            score += 10_000 + victim.getValue() * 10 - move.getPiece().getValue();
        }
        if (move instanceof Promotion) { // turning a pawn into a queen is almost always worth looking at early
            score += 9_000;
        }
        return score;
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
