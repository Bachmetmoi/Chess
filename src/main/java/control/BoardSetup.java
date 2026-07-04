package control;

import entity.board.Cell;
import entity.board.ChessBoard;
import entity.enums.BoardColor;
import entity.enums.Faction;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Queen;
import entity.pieces.Rook;

public class BoardSetup {
    // methods
    public ChessBoard setUp() {
        ChessBoard chessBoard = blankBoard();
        Cell[][] board = chessBoard.getBoard();

        // place pieces
        board[0][0].setContain(new Rook(Faction.WHITE));
        board[1][0].setContain(new Knight(Faction.WHITE));
        board[2][0].setContain(new Bishop(Faction.WHITE));
        board[3][0].setContain(new Queen(Faction.WHITE));
        board[4][0].setContain(new King(Faction.WHITE));
        board[5][0].setContain(new Bishop(Faction.WHITE));
        board[6][0].setContain(new Knight(Faction.WHITE));
        board[7][0].setContain(new Rook(Faction.WHITE));
        board[0][7].setContain(new Rook(Faction.BLACK));
        board[1][7].setContain(new Knight(Faction.BLACK));
        board[2][7].setContain(new Bishop(Faction.BLACK));
        board[3][7].setContain(new Queen(Faction.BLACK));
        board[4][7].setContain(new King(Faction.BLACK));
        board[5][7].setContain(new Bishop(Faction.BLACK));
        board[6][7].setContain(new Knight(Faction.BLACK));
        board[7][7].setContain(new Rook(Faction.BLACK));
        for (int m = 0; m < 8; m++) {
            board[m][1].setContain(new Pawn(Faction.WHITE));
        }
        for (int n = 0; n < 8; n++) {
            board[n][6].setContain(new Pawn(Faction.BLACK));
        }

        return chessBoard;

    }

    /**
     * Builds an empty board with every cell created and coloured, but no pieces.
     * Shared by {@link #setUp()} and by {@link CustomBoardSetup}, which fills the
     * cells from a hand-built position.
     */
    protected ChessBoard blankBoard() {
        ChessBoard chessBoard = new ChessBoard();
        Cell[][] board = chessBoard.getBoard();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                BoardColor c = ((i + j) % 2 == 0) ? BoardColor.BLACK : BoardColor.WHITE;
                board[i][j] = new Cell(i, j, c);
            }
        }
        return chessBoard;
    }
}
