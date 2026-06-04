package entity.pieces;

import java.util.ArrayList;
import java.util.List;

import entity.board.Cell;
import entity.enums.Faction;
import entity.move.Castling;
import entity.move.Move;
import entity.move.NormalMove;

public class King extends Piece {
    // constructor
    public King(Faction side) {
        super(0, side);
    }

    // methods
    @Override
    public List<Move> move(Cell[][] board, int curX, int curY) {
        List<Move> moves = new ArrayList<>();
        int[][] direction = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 } };
        for (int[] dir : direction) {
            // not legal
            if (curX + dir[0] >= 8 || curX + dir[0] < 0 || curY + dir[1] >= 8 || curY + dir[1] < 0) {
                continue;
            }

            // check if have another piece same color
            else if ((board[curX + dir[0]][curY + dir[1]].getContain() == null)) {
                moves.add(new NormalMove(curX, curY, curX + dir[0], curY + dir[1], this, null));
            }

            else if (board[curX + dir[0]][curY + dir[1]].getContain().getSide() == this.getSide()) {
                continue;
            }

            else if (board[curX + dir[0]][curY + dir[1]].getContain().getSide() != this.getSide()) {
                moves.add(
                        new NormalMove(curX, curY, curX + dir[0], curY + dir[1], this,
                                board[curX + dir[0]][curY + dir[1]].getContain()));
            }
        }

        // check Castling
        // short castling
        if (curX == 4 && this.getMoveCount() == 0 && board[curX + 1][curY].getContain() == null
                && board[curX + 2][curY].getContain() == null
                && board[curX + 3][curY].getContain() instanceof Rook
                && board[curX + 3][curY].getContain().getMoveCount() == 0) {
            moves.add(
                    new Castling(curX, curY, curX + 2, curY, this, curX + 1, curY, board[curX + 3][curY].getContain()));
        }

        // long castling
        if (curX == 4 && this.getMoveCount() == 0 && board[curX - 1][curY].getContain() == null
                && board[curX - 2][curY].getContain() == null
                && board[curX - 3][curY].getContain() == null
                && board[curX - 4][curY].getContain() instanceof Rook
                && board[curX - 4][curY].getContain().getMoveCount() == 0) {
            moves.add(
                    new Castling(curX, curY, curX - 2, curY, this, curX - 1, curY, board[curX - 4][curY].getContain()));
        }
        return moves;
    }
}
