package control; // same package as GameController/LegalMove so the engine sits with the other game logic

import java.util.ArrayList; // a growable list, used to collect the legal moves we generate
import java.util.List; // the List interface type we pass around

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

    // Bonus for a king that has castled, in the same units as material (a pawn is
    // worth ~1, see Pawn's value). 50 is about half a pawn: enough that the engine
    // will castle when nothing more valuable is at stake, but not so much that it
    // ignores a real capture to do it. Tune this up to make it castle more eagerly.
    private static final int CASTLE_BONUS = 50;

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

        if (depth == 0) { // reached the lookahead limit: stop recursing
            return evaluate(); // just score the position statically and return it
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
        for (int x = 0; x < 8; x++) { // walk every file (column)
            for (int y = 0; y < 8; y++) { // walk every rank (row)
                Piece piece = board[x][y].getContain(); // the piece on this square, or null if empty
                if (piece == null) { // empty square contributes nothing
                    continue; // skip to the next square
                }
                if (piece.getSide() == Faction.WHITE) { // a White piece helps White
                    score += piece.getValue() * 100; // so add its value (×100 -> centipawns)
                } else { // a Black piece helps Black
                    score -= piece.getValue() * 100; // so subtract its value (×100 -> centipawns)
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

        return score; // material balance plus king-safety bonus, from White's view
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
