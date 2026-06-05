package entity.move;

import entity.pieces.Piece;

public abstract class Move {
    // attributes
    private int startXPos;
    private int startYPos;
    private int endXPos;
    private int endYPos;
    private Piece piece;

    private int moveUntilDrawBefore;

    // constructor
    public Move(int startXPos, int startYPos, int endXPos, int endYPos, Piece piece) {
        this.startXPos = startXPos;
        this.startYPos = startYPos;
        this.endXPos = endXPos;
        this.endYPos = endYPos;
        this.piece = piece;
    }

    // methods
    public int getStartXPos() {
        return startXPos;
    }

    public int getStartYPos() {
        return startYPos;
    }

    public int getEndXPos() {
        return endXPos;
    }

    public int getEndYPos() {
        return endYPos;
    }

    public Piece getPiece() {
        return piece;
    }

    public int getMoveUntilDrawBefore() {
        return moveUntilDrawBefore;
    }

    public void setMoveUntilDrawBefore(int value) {
        moveUntilDrawBefore = value;
    }
}
