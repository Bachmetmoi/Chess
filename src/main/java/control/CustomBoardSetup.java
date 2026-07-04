package control;

import entity.board.Cell;
import entity.board.ChessBoard;
import entity.enums.Faction;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;

/**
 * Builds a board from a hand-made position (the "Customize" editor) instead of
 * the standard start. The placement is indexed {@code [x][y]} (file, rank) to
 * match {@link entity.board.ChessBoard#getBoard()}; a {@code null} entry is an
 * empty square. Pieces are cloned into fresh instances so the game never shares
 * mutable state (e.g. move counts) with the editor that supplied them.
 */
public class CustomBoardSetup extends BoardSetup {
    private final Piece[][] placement;

    public CustomBoardSetup(Piece[][] placement) {
        this.placement = placement;
    }

    @Override
    public ChessBoard setUp() {
        ChessBoard chessBoard = blankBoard();
        Cell[][] board = chessBoard.getBoard();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                board[x][y].setContain(copyOf(placement[x][y]));
            }
        }
        return chessBoard;
    }

    /** Returns a fresh piece of the same type and side, or {@code null}. */
    private Piece copyOf(Piece p) {
        if (p == null) {
            return null;
        }
        Faction s = p.getSide();
        if (p instanceof King) {
            return new King(s);
        }
        if (p instanceof Queen) {
            return new Queen(s);
        }
        if (p instanceof Rook) {
            return new Rook(s);
        }
        if (p instanceof Bishop) {
            return new Bishop(s);
        }
        if (p instanceof Knight) {
            return new Knight(s);
        }
        return new Pawn(s);
    }
}
