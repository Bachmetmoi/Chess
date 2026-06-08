package entity.move;

import entity.pieces.Piece;

public class EnPassant extends SpecialMove {
    // attributes
    private Piece capture; // Capture Pawn

    // constructor
    public EnPassant(int startXPos, int startYPos, int endXPos, int endYPos, Piece piece, Piece capturePiece) {
        super(startXPos, startYPos, endXPos, endYPos, piece);
        capture = capturePiece;
    }

    // methods
    public Piece getCapturePiece() {
        return capture;
    }
}
