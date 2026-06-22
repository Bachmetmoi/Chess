package control;

import entity.board.Cell;
import entity.board.ChessBoard;
import entity.enums.Faction;
import entity.move.Castling;
import entity.move.Move;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;

public class LegalMove {
    // attributes
    private ChessBoard chessBoard;

    // constructor
    public LegalMove(ChessBoard c) {
        chessBoard = c;
    }

    // methods
    private boolean kingSafe(Cell[][] board, Piece sameSidePiece) {
        Faction side = sameSidePiece.getSide();

        // find king
        int kingX = -1, kingY = -1;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Piece p = board[i][j].getContain();
                if (p instanceof King && p.getSide() == side) {
                    kingX = i;
                    kingY = j;
                }
            }
        }
        if (kingX == -1) {
            return true; // no king on the board (shouldn't happen) -> nothing to attack
        }

        Faction enemy = (side == Faction.WHITE) ? Faction.BLACK : Faction.WHITE;
        return !isSquareAttacked(board, kingX, kingY, enemy);
    }

    /**
     * Returns true if square (x, y) is attacked by any piece of {@code attacker}.
     *
     * This replaces the old "generate every enemy move and see if one lands on the
     * king" scan. Instead it looks OUTWARD from the target square for the specific
     * piece types that could attack it: pawns and knights at fixed offsets, the
     * enemy king on adjacent squares, and sliding pieces (rook/bishop/queen) along
     * rays until the first blocker. Same result, far less work -- it never builds a
     * move list and stops each ray at the first piece it meets.
     */
    private boolean isSquareAttacked(Cell[][] board, int x, int y, Faction attacker) {
        // Pawns: a White pawn attacks one rank UP (+y), a Black pawn one rank DOWN
        // (-y). So an attacking pawn that hits (x, y) sits on the rank the attacker
        // came FROM: y-1 for White, y+1 for Black.
        int pawnRow = (attacker == Faction.WHITE) ? y - 1 : y + 1;
        for (int dx = -1; dx <= 1; dx += 2) {
            int px = x + dx;
            if (inBounds(px, pawnRow)) {
                Piece p = board[px][pawnRow].getContain();
                if (p instanceof Pawn && p.getSide() == attacker) {
                    return true;
                }
            }
        }

        // Knights
        int[][] knightMoves = { { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 },
                { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 } };
        for (int[] d : knightMoves) {
            int nx = x + d[0], ny = y + d[1];
            if (inBounds(nx, ny)) {
                Piece p = board[nx][ny].getContain();
                if (p instanceof Knight && p.getSide() == attacker) {
                    return true;
                }
            }
        }

        // Enemy king on an adjacent square
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny)) {
                    Piece p = board[nx][ny].getContain();
                    if (p instanceof King && p.getSide() == attacker) {
                        return true;
                    }
                }
            }
        }

        // Sliding pieces along straight lines: rook or queen
        int[][] orthogonal = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        if (rayHits(board, x, y, orthogonal, attacker, true)) {
            return true;
        }

        // Sliding pieces along diagonals: bishop or queen
        int[][] diagonal = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        if (rayHits(board, x, y, diagonal, attacker, false)) {
            return true;
        }

        return false;
    }

    /**
     * Walks each direction in {@code directions} out from (x, y) until it leaves the
     * board or meets a piece. If that first piece belongs to {@code attacker} and is
     * the right sliding type for these rays (rook/queen for straight lines when
     * {@code straight} is true, bishop/queen for diagonals otherwise), the square is
     * attacked.
     */
    private boolean rayHits(Cell[][] board, int x, int y, int[][] directions, Faction attacker, boolean straight) {
        for (int[] d : directions) {
            int nx = x + d[0], ny = y + d[1];
            while (inBounds(nx, ny)) {
                Piece p = board[nx][ny].getContain();
                if (p != null) {
                    if (p.getSide() == attacker
                            && (p instanceof Queen || (straight ? p instanceof Rook : p instanceof Bishop))) {
                        return true;
                    }
                    break; // blocked by the first piece on this ray
                }
                nx += d[0];
                ny += d[1];
            }
        }
        return false;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8;
    }

    public boolean isLegal(Move move) {
        int startX = move.getStartXPos();
        int startY = move.getStartYPos();
        int endX = move.getEndXPos();
        int endY = move.getEndYPos();
        int direction = (endX > startX) ? 1 : -1; // castling short/long
        Cell[][] board = chessBoard.getBoard();
        Piece movingPiece = board[startX][startY].getContain();
        Piece capturedPiece = board[endX][endY].getContain();
        boolean legal = true;

        // castling check
        if (move instanceof Castling) {
            if (!kingSafe(board, movingPiece)) {
                return false;
            }

            // If King pass through check
            board[startX][startY].setContain(null);
            board[startX + direction][startY].setContain(movingPiece);

            if (!(kingSafe(board, movingPiece))) {
                legal = false;
            }

            // undo
            board[startX][startY].setContain(movingPiece);
            board[startX + direction][startY].setContain(null);

            if (!legal) {
                return false;
            }
        }
        // normal move
        // try to move
        board[endX][endY].setContain(movingPiece);
        board[startX][startY].setContain(null);

        // check if King in check
        if (!kingSafe(board, movingPiece)) {
            legal = false;
        }

        // undo move
        board[startX][startY].setContain(movingPiece);
        board[endX][endY].setContain(capturedPiece);

        return legal;
    }
}
