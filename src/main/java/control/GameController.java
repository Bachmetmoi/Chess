package control;

import java.util.ArrayList;
import java.util.List;

import entity.board.Cell;
import entity.board.ChessBoard;
import entity.enums.Faction;
import entity.enums.Result;
import entity.move.Castling;
import entity.move.EnPassant;
import entity.move.Move;
import entity.move.NormalMove;
import entity.move.Promotion;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;
import entity.state.GameState;

public class GameController {
    // attributes
    private GameState gameState;
    private LegalMove legalMove;
    private int moveUntilDraw = 0;

    // methods
    public List<Move> getMoves(GameState gameState) {
        List<Move> moves = new ArrayList<>();
        Cell[][] board = gameState.getChessBoard().getBoard();
        List<Move> history = gameState.getMoveHistory();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Piece p = board[i][j].getContain();
                if (p != null && p.getSide() == gameState.getTurn()) {
                    for (Move m : p.move(board, i, j, history)) {
                        if (legalMove.isLegal(m)) {
                            moves.add(m);
                        }
                    }
                }
            }
        }
        return moves;
    }

    public void startGame(BoardSetup board) {
        // setup Board
        ChessBoard b = board.setUp();
        List<Move> move = new ArrayList<>();
        gameState = new GameState(Faction.WHITE, move, Result.ONGOING, b);
        legalMove = new LegalMove(b);
        moveUntilDraw = 0;
    }

    public boolean validate(Move move) {
        if (move.getPiece().getSide() != gameState.getTurn()) {
            return false;
        }
        return legalMove.isLegal(move);
    }

    public GameState executes(Move move) {
        // update chessBoard
        Cell[][] board = gameState.getChessBoard().getBoard();
        int startX = move.getStartXPos();
        int startY = move.getStartYPos();
        int endX = move.getEndXPos();
        int endY = move.getEndYPos();
        Piece movingPiece = board[startX][startY].getContain();

        move.setMoveUntilDrawBefore(moveUntilDraw);

        // check Castling
        if (move instanceof Castling) {
            // short
            if (((Castling) move).getSecondPieceEndXPos() == 5) {
                Piece secondPiece = board[startX + 3][startY].getContain();
                board[startX][startY].setContain(null);
                board[startX + 3][startY].setContain(null);
                board[endX][endY].setContain(movingPiece);
                board[startX + 1][startY].setContain(secondPiece);
            }

            // long
            if (((Castling) move).getSecondPieceEndXPos() == 3) {
                Piece secondPiece = board[0][startY].getContain();
                board[startX][startY].setContain(null);
                board[0][startY].setContain(null);
                board[endX][endY].setContain(movingPiece);
                board[startX - 1][startY].setContain(secondPiece);
            }

        }

        else if (move instanceof EnPassant) {
            board[startX][startY].setContain(null);
            board[endX][startY].setContain(null);
            board[endX][endY].setContain(movingPiece);
        }

        else if (move instanceof Promotion) {
            Piece promoted = ((Promotion) move).getPiecePromoted();
            board[startX][startY].setContain(null);
            board[endX][endY].setContain(promoted);
        }

        else {
            board[startX][startY].setContain(null);
            board[endX][endY].setContain(movingPiece);
        }

        // reset 50 move rule
        if (move.getPiece() instanceof Pawn) {
            moveUntilDraw = 0;
        }

        else if (move instanceof NormalMove && ((NormalMove) move).getCapturePiece() != null) {
            moveUntilDraw = 0;
        }

        else {
            moveUntilDraw += 1;
        }

        // change isFirstMove
        movingPiece.addMoveCount();

        // add to move History
        gameState.addMoveHistory(move);

        // change turn
        if (gameState.getTurn() == Faction.WHITE)
            gameState.setTurn(Faction.BLACK);
        else
            gameState.setTurn(Faction.WHITE);
        return gameState;

    }

    public GameState undoMove() {
        // edge cases
        if (gameState.getMoveHistory().size() == 0) {
            System.out.println("This is the start position. You can't undo!");
            return gameState;
        }

        // un-count the position we are leaving so a taken-back repetition does not
        // keep counting toward a threefold draw. Must run before the board, turn
        // and history are reverted, so positionKey() still matches the key that
        // checkGameStatus() recorded for this move.
        gameState.unrecordPosition(positionKey());

        Cell[][] board = gameState.getChessBoard().getBoard();
        Move undo = gameState.getMoveHistory().remove(gameState.getMoveHistory().size() - 1);
        Piece movingPiece = undo.getPiece();
        int startX = undo.getStartXPos();
        int startY = undo.getStartYPos();
        int endX = undo.getEndXPos();
        int endY = undo.getEndYPos();

        // normal move
        if (undo instanceof NormalMove) {
            Piece capturedPiece = ((NormalMove) undo).getCapturePiece();

            // revert position
            board[endX][endY].setContain(capturedPiece);
            board[startX][startY].setContain(movingPiece);

        }

        else {
            if (undo instanceof Castling) {
                // short
                if ((((Castling) undo).getSecondPieceEndXPos() == 5)) {
                    Piece secondPiece = board[startX + 1][startY].getContain();
                    board[endX][endY].setContain(null);
                    board[((Castling) undo).getSecondPieceEndXPos()][((Castling) undo).getSecondPieceEndYPos()]
                            .setContain(null);
                    board[startX][startY].setContain(movingPiece);
                    board[startX + 3][startY].setContain(secondPiece);
                }

                // long
                if ((((Castling) undo).getSecondPieceEndXPos() == 3)) {
                    Piece secondPiece = board[startX - 1][startY].getContain();
                    board[endX][endY].setContain(null);
                    board[((Castling) undo).getSecondPieceEndXPos()][((Castling) undo).getSecondPieceEndYPos()]
                            .setContain(null);
                    board[startX][startY].setContain(movingPiece);
                    board[startX - 4][startY].setContain(secondPiece);
                }
            }

            if (undo instanceof EnPassant) {
                Piece capturedPiece = ((EnPassant) undo).getCapturePiece();

                board[startX][startY].setContain(movingPiece);
                board[endX][startY].setContain(capturedPiece);
                board[endX][endY].setContain(null);
            }

            if (undo instanceof Promotion) {
                Pawn pawn = new Pawn(movingPiece.getSide());
                Piece capturePiece = ((Promotion) undo).getCapturePiece();
                board[startX][startY].setContain(pawn);
                board[endX][endY].setContain(capturePiece);
            }
        }

        // change turn
        if (gameState.getTurn() == Faction.WHITE)
            gameState.setTurn(Faction.BLACK);
        else
            gameState.setTurn(Faction.WHITE);

        // reduce move counter
        movingPiece.reduceMoveCount();

        // reduce moveUntilDraw
        moveUntilDraw = undo.getMoveUntilDrawBefore();

        return gameState;
    }

    public Result checkGameStatus() {
        // threefold repetition: if the same position is reached three times the
        // game is a draw, even when legal moves are still available
        if (gameState.recordPosition(positionKey()) >= 3) {
            gameState.setGameStatus(Result.DRAW);
            return Result.DRAW;
        }

        // if it is check and no moves --> checkmate
        // if it is not check and no moves --> draw
        // else: ongoing

        if (!getMoves(gameState).isEmpty()) {
            return Result.ONGOING;
        }

        Cell[][] board = gameState.getChessBoard().getBoard();
        List<Move> history = gameState.getMoveHistory();

        // if no legal moves exist
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                // find other side pieces
                if (board[i][j].getContain() == null || board[i][j].getContain().getSide() == gameState.getTurn()) {
                    continue;
                } else {
                    List<Move> moves = board[i][j].getContain().move(board, i, j, history);
                    for (Move m : moves) {
                        // find check
                        if (board[m.getEndXPos()][m.getEndYPos()].getContain() instanceof King
                                && board[m.getEndXPos()][m.getEndYPos()].getContain().getSide() == gameState
                                        .getTurn()) {
                            if (gameState.getTurn() == Faction.WHITE) {
                                gameState.setGameStatus(Result.BLACK_WIN);
                                return Result.BLACK_WIN;
                            } else {
                                gameState.setGameStatus(Result.WHITE_WIN);
                                return Result.WHITE_WIN;
                            }
                        }
                    }
                }
            }
        }
        gameState.setGameStatus(Result.DRAW);
        return Result.DRAW;

    }

    /**
     * Builds a string that uniquely identifies the current position for the
     * threefold repetition rule. Two positions count as the same only when they
     * share the same piece placement, side to move, castling rights and en
     * passant possibility (the same fields a FEN string uses).
     */
    private String positionKey() {
        Cell[][] board = gameState.getChessBoard().getBoard();
        StringBuilder sb = new StringBuilder();

        // piece placement
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Piece p = board[x][y].getContain();
                sb.append(p == null ? '.' : pieceSymbol(p));
            }
        }

        // side to move
        sb.append(' ').append(gameState.getTurn() == Faction.WHITE ? 'w' : 'b');
        // castling rights
        sb.append(' ').append(castlingRights());
        // en passant target square
        sb.append(' ').append(enPassantTarget());

        return sb.toString();
    }

    private char pieceSymbol(Piece p) {
        char c;
        if (p instanceof King) {
            c = 'k';
        } else if (p instanceof Queen) {
            c = 'q';
        } else if (p instanceof Rook) {
            c = 'r';
        } else if (p instanceof Bishop) {
            c = 'b';
        } else if (p instanceof Knight) {
            c = 'n';
        } else {
            c = 'p'; // Pawn
        }
        return p.getSide() == Faction.WHITE ? Character.toUpperCase(c) : c;
    }

    private String castlingRights() {
        StringBuilder sb = new StringBuilder();
        // White king on e1 (4,0); rooks on h1 (7,0) and a1 (0,0)
        if (canCastle(4, 0, 7, 0, Faction.WHITE)) {
            sb.append('K');
        }
        if (canCastle(4, 0, 0, 0, Faction.WHITE)) {
            sb.append('Q');
        }
        // Black king on e8 (4,7); rooks on h8 (7,7) and a8 (0,7)
        if (canCastle(4, 7, 7, 7, Faction.BLACK)) {
            sb.append('k');
        }
        if (canCastle(4, 7, 0, 7, Faction.BLACK)) {
            sb.append('q');
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private boolean canCastle(int kingX, int kingY, int rookX, int rookY, Faction side) {
        Cell[][] board = gameState.getChessBoard().getBoard();
        Piece king = board[kingX][kingY].getContain();
        Piece rook = board[rookX][rookY].getContain();
        return king instanceof King && king.getSide() == side && king.getMoveCount() == 0
                && rook instanceof Rook && rook.getSide() == side && rook.getMoveCount() == 0;
    }

    private String enPassantTarget() {
        List<Move> history = gameState.getMoveHistory();
        if (history.isEmpty()) {
            return "-";
        }
        Move last = history.get(history.size() - 1);
        // a pawn that just advanced two squares leaves an en passant target on the
        // square it skipped over
        if (last.getPiece() instanceof Pawn && Math.abs(last.getStartYPos() - last.getEndYPos()) == 2) {
            int targetY = (last.getStartYPos() + last.getEndYPos()) / 2;
            return "" + last.getEndXPos() + targetY;
        }
        return "-";
    }

    public GameState getGameState() {
        return gameState;
    }

    public LegalMove getLegalMove() {
        return legalMove;
    }
}
