package control;

import entity.board.Cell;
import entity.board.ChessBoard;
import entity.enums.Faction;
import entity.move.Castling;
import entity.move.EnPassant;
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

    /**
     * Locates {@code side}'s king on {@code board}, returned as {@code {x, y}} (or
     * {@code {-1, -1}} if there is none, which should not happen in a real game).
     */
    private int[] findKing(Cell[][] board, Faction side) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Piece p = board[i][j].getContain();
                if (p instanceof King && p.getSide() == side) {
                    return new int[] { i, j };
                }
            }
        }
        return new int[] { -1, -1 };
    }

    /**
     * Public helper for the search: the square of {@code side}'s king on the current
     * board, as {@code {x, y}}. The engine reads this ONCE per position and then
     * reuses it across every move it checks (see {@link #isLegalWithKing}), instead
     * of rescanning the board for the king on every single move.
     */
    public int[] findKing(Faction side) {
        return findKing(chessBoard.getBoard(), side);
    }

    private boolean kingSafe(Cell[][] board, Piece sameSidePiece) {
        Faction side = sameSidePiece.getSide();
        int[] king = findKing(board, side);
        if (king[0] == -1) {
            return true; // no king on the board (shouldn't happen) -> nothing to attack
        }
        Faction enemy = (side == Faction.WHITE) ? Faction.BLACK : Faction.WHITE;
        return !isSquareAttacked(board, king[0], king[1], enemy);
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
        // Stand-alone entry (e.g. validating a human move): find the king ourselves,
        // then run the shared check. The search uses isLegalWithKing directly so it
        // can find the king once per position rather than once per move.
        Cell[][] board = chessBoard.getBoard();
        Piece movingPiece = board[move.getStartXPos()][move.getStartYPos()].getContain();
        int[] king = findKing(board, movingPiece.getSide());
        return isLegalWithKing(move, king[0], king[1]);
    }

    /**
     * The body of {@link #isLegal}, but told where the side-to-move's king stands
     * ({@code kingX}, {@code kingY}) on the current board so it need not rescan for
     * it. For a normal move the king square is unchanged (or, if the king itself
     * moves, its destination); castling keeps the original pass-through-check logic.
     * The board is mutated to test the move and restored before returning, so the
     * position is left exactly as it was found.
     */
    public boolean isLegalWithKing(Move move, int kingX, int kingY) {
        int startX = move.getStartXPos();
        int startY = move.getStartYPos();
        int endX = move.getEndXPos();
        int endY = move.getEndYPos();
        Cell[][] board = chessBoard.getBoard();
        Piece movingPiece = board[startX][startY].getContain();
        Piece capturedPiece = board[endX][endY].getContain();

        // castling check: the king must be safe where it stands, and on the square it
        // steps through. (Its landing square is checked by the normal-move test below.)
        if (move instanceof Castling) {
            int direction = (endX > startX) ? 1 : -1; // short/long
            if (!kingSafe(board, movingPiece)) {
                return false;
            }
            board[startX][startY].setContain(null);
            board[startX + direction][startY].setContain(movingPiece);
            boolean passSafe = kingSafe(board, movingPiece);
            board[startX][startY].setContain(movingPiece);
            board[startX + direction][startY].setContain(null);
            if (!passSafe) {
                return false;
            }
        }

        // en passant: the captured pawn is NOT on the landing square but beside the
        // start square, so lift it off for the test (and restore it below).
        Piece enPassantVictim = null;
        if (move instanceof EnPassant) {
            enPassantVictim = board[endX][startY].getContain();
            board[endX][startY].setContain(null);
        }

        // normal move: play it, see whether our king is attacked, then take it back
        board[endX][endY].setContain(movingPiece);
        board[startX][startY].setContain(null);

        // If the king itself moved, it now stands on the destination; otherwise it is
        // wherever the caller said it was.
        boolean movingKing = movingPiece instanceof King;
        int kx = movingKing ? endX : kingX;
        int ky = movingKing ? endY : kingY;
        Faction enemy = (movingPiece.getSide() == Faction.WHITE) ? Faction.BLACK : Faction.WHITE;
        boolean legal = !isSquareAttacked(board, kx, ky, enemy);

        board[startX][startY].setContain(movingPiece);
        board[endX][endY].setContain(capturedPiece);
        if (enPassantVictim != null) {
            board[endX][startY].setContain(enPassantVictim);
        }

        return legal;
    }
}
