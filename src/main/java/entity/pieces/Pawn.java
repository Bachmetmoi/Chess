package entity.pieces;

import java.util.ArrayList;
import java.util.List;

import entity.board.Cell;
import entity.enums.Faction;
import entity.move.EnPassant;
import entity.move.Move;
import entity.move.NormalMove;
import entity.move.Promotion;

public class Pawn extends Piece {
    // constructor
    public Pawn(Faction side) {
        super(1, side);
    }

    // methods
    @Override
    public List<Move> move(Cell[][] board, int curX, int curY) {
        return move(board, curX, curY, new ArrayList<>());
    }

    @Override
    public List<Move> move(Cell[][] board, int curX, int curY, List<Move> moveHistory) {
        /*
         * Pawn moves
         * 1) Move forward 1 square
         * 2) If in home square, move forward 2 squares
         * 3) Capture diagonally
         * 4) En passant
         * 5) Promotion
         */
        List<Move> moves = new ArrayList<>();

        int direction;
        int startRow; // part 2
        int lastRow; // part 5
        if (this.getSide() == Faction.WHITE) {
            direction = 1;
            startRow = 1;
            lastRow = 7;
        } else {
            direction = -1;
            startRow = 6;
            lastRow = 0;
        }

        // part 5:
        if (curY + direction == lastRow) {
            // forward
            if (curX < 8 && curX >= 0 && board[curX][curY + direction].getContain() == null) {
                moves.add(new Promotion(curX, curY, curX, curY + direction, this, null, null));
            }

            // capture
            if (curX + 1 < 8 && curX + 1 >= 0 && board[curX + 1][curY + direction].getContain() != null
                    && board[curX + 1][curY + direction].getContain().getSide() != this.getSide()) {
                moves.add(new Promotion(curX, curY, curX + 1, curY + direction, this, null,
                        board[curX + 1][curY + direction].getContain()));
            }

            if (curX - 1 < 8 && curX - 1 >= 0 && board[curX - 1][curY + direction].getContain() != null
                    && board[curX - 1][curY + direction].getContain().getSide() != this.getSide()) {
                moves.add(new Promotion(curX, curY, curX - 1, curY + direction, this, null,
                        board[curX - 1][curY + direction].getContain()));
            }
        } else {
            // part 1:
            if (curY + direction >= 0 && curY + direction < 8 && board[curX][curY + direction].getContain() == null) {
                moves.add(new NormalMove(curX, curY, curX, curY + direction, this, null));
            }

            // part 2:
            if (curY == startRow && board[curX][curY + direction].getContain() == null
                    && board[curX][curY + direction * 2].getContain() == null) {
                moves.add(new NormalMove(curX, curY, curX, curY + direction * 2, this, null));
            }

            // part 3:
            if (curX + 1 < 8 && curY + direction >= 0 && curY + direction < 8
                    && board[curX + 1][curY + direction].getContain() != null
                    && board[curX + 1][curY + direction].getContain().getSide() != this.getSide()) {
                moves.add(new NormalMove(curX, curY, curX + 1, curY + direction, this,
                        board[curX + 1][curY + direction].getContain()));
            }

            if (curX - 1 >= 0 && curY + direction >= 0 && curY + direction < 8
                    && board[curX - 1][curY + direction].getContain() != null
                    && board[curX - 1][curY + direction].getContain().getSide() != this.getSide()) {
                moves.add(new NormalMove(curX, curY, curX - 1, curY + direction, this,
                        board[curX - 1][curY + direction].getContain()));
            }

            // part 4:
            // check if pawn nearby
            if (!(moveHistory).isEmpty()) {
                Move lastMove = moveHistory.get(moveHistory.size() - 1);
                if (curX - 1 >= 0 && board[curX - 1][curY].getContain() instanceof Pawn
                        && board[curX - 1][curY].getContain().getSide() != this.getSide()
                        && lastMove.getStartXPos() == curX - 1 && lastMove.getStartYPos() == curY + 2 * direction
                        && lastMove.getEndXPos() == curX - 1 && lastMove.getEndYPos() == curY) {
                    moves.add(new EnPassant(curX, curY, curX - 1, curY + direction, this,
                            board[curX - 1][curY].getContain()));
                }

                if (curX + 1 < 8 && board[curX + 1][curY].getContain() instanceof Pawn
                        && board[curX + 1][curY].getContain().getSide() != this.getSide()
                        && lastMove.getStartXPos() == curX + 1 && lastMove.getStartYPos() == curY + 2 * direction
                        && lastMove.getEndXPos() == curX + 1 && lastMove.getEndYPos() == curY) {
                    moves.add(new EnPassant(curX, curY, curX + 1, curY + direction, this,
                            board[curX + 1][curY].getContain()));
                }
            }
        }
        return moves;
    }

}
