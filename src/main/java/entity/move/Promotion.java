package entity.move;

import entity.pieces.Piece;

public class Promotion extends SpecialMove {
    // attributes
    private Piece promoteTo;
    private Piece capture;

    // constructor
    public Promotion(int startXPos, int startYPos, int endXPos, int endYPos, Piece piece, Piece promoted,
            Piece capturePiece) {
        super(startXPos, startYPos, endXPos, endYPos, piece);
        promoteTo = promoted;
        capture = capturePiece;
    }

    // methods
    public Piece getPiecePromoted() {
        return promoteTo;
    }

    public Piece getCapturePiece() {
        return capture;
    }
}
