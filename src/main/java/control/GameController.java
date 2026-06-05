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
import entity.pieces.King;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.state.GameState;

public class GameController {
    // attributes
    private GameState gameState;
    private LegalMove legalMove;
    private int moveUntilDraw = 0;

    // methods
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
        // if it is check and no moves --> checkmate
        // if it is not check and no moves --> draw
        // else: ongoing

        Cell[][] board = gameState.getChessBoard().getBoard();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                // find same side pieces
                if (board[i][j].getContain() == null || board[i][j].getContain().getSide() != gameState.getTurn()) {
                    continue;
                } else {
                    List<Move> moves = board[i][j].getContain().move(board, i, j);
                    for (Move m : moves) {
                        // if legal moves exist --> game not end
                        if (legalMove.isLegal(m))
                            return Result.ONGOING;
                    }
                }

            }
        }

        // if no legal moves exist
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                // find other side pieces
                if (board[i][j].getContain() == null || board[i][j].getContain().getSide() == gameState.getTurn()) {
                    continue;
                } else {
                    List<Move> moves = board[i][j].getContain().move(board, i, j);
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

    public GameState getGameState() {
        return gameState;
    }

    public LegalMove getLegalMove() {
        return legalMove;
    }
}
