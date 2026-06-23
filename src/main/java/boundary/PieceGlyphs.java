package boundary;

import entity.enums.Faction;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Shared piece rendering for the board screens. Both the live game board and the
 * trailer board draw pieces the same way, so the glyph/colour logic lives here
 * once instead of being copied into each screen.
 */
final class PieceGlyphs {
    private PieceGlyphs() {
    }

    /** A coloured, outlined text glyph for {@code piece} at the given font size. */
    static Text glyph(Piece piece, double fontSize) {
        Text t = new Text(symbol(piece));
        t.setFont(Font.font("Segoe UI Symbol", fontSize));
        if (piece.getSide() == Faction.WHITE) {
            t.setFill(Color.WHITE);
            t.setStroke(Color.web("#333333"));
            t.setStrokeWidth(1.2);
        } else {
            t.setFill(Color.web("#202020"));
            t.setStroke(Color.web("#202020"));
            t.setStrokeWidth(1.2);
        }
        return t;
    }

    /**
     * The Unicode chess glyph for a piece. Solid (filled) glyphs are used for both
     * colours; the fill colour set in {@link #glyph} distinguishes white from
     * black, which reads best on the board.
     */
    static String symbol(Piece piece) {
        if (piece instanceof King) {
            return "♚";
        }
        if (piece instanceof Queen) {
            return "♛";
        }
        if (piece instanceof Rook) {
            return "♜";
        }
        if (piece instanceof Bishop) {
            return "♝";
        }
        if (piece instanceof Knight) {
            return "♞";
        }
        if (piece instanceof Pawn) {
            return "♟";
        }
        return "";
    }
}
