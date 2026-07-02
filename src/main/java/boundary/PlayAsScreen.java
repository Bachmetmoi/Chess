package boundary;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Reached from "Engine": the human picks which colour to play. Choosing the
 * white king starts a normal game (the engine answers as Black); choosing the
 * black king flips the board and lets the engine, now playing White, move first.
 */
public class PlayAsScreen extends Screen {

    // A king glyph big enough to fill its button; same solid symbol as the board,
    // its colour set on the Text so white and black kings are told apart.
    private static final String KING = "♚";
    private static final double GLYPH_SIZE = 96;

    public PlayAsScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        // White king: the human plays White, so the engine plays Black.
        Button whiteKing = kingButton(true);
        whiteKing.setOnAction(e -> navigator.showEngineGame(false));

        // Black king: the human plays Black, so the engine plays White (board
        // flipped, engine moves first).
        Button blackKing = kingButton(false);
        blackKing.setOnAction(e -> navigator.showEngineGame(true));

        HBox choices = new HBox(40, whiteKing, blackKing);
        choices.setAlignment(Pos.CENTER);

        Button back = secondaryButton("Back");
        back.setOnAction(e -> navigator.showModeSelect());

        VBox box = rootBox(titleLabel("PLAY AS"), choices, back);
        return box;
    }

    /**
     * Builds one colour-choice button: a chessboard-style square holding a king
     * glyph. The white king sits on a light square with a white (dark-outlined)
     * glyph; the black king sits on a dark square with a dark glyph, so both read
     * clearly against the menu background.
     */
    private Button kingButton(boolean white) {
        Text glyph = new Text(KING);
        glyph.setFont(Font.font("Segoe UI Symbol", GLYPH_SIZE));
        if (white) {
            glyph.setFill(Color.WHITE);
            glyph.setStroke(Color.web("#333333"));
            glyph.setStrokeWidth(1.5);
        } else {
            glyph.setFill(Color.web("#202020"));
            glyph.setStroke(Color.web("#202020"));
            glyph.setStrokeWidth(1.5);
        }

        Button b = new Button();
        b.setGraphic(glyph);
        b.setPrefSize(150, 150);
        String base = white ? SQUARE_LIGHT : SQUARE_DARK;
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base + HIGHLIGHT));
        b.setOnMouseExited(e -> b.setStyle(base));
        return b;
    }

    private static final String SQUARE_LIGHT =
        "-fx-background-color:#EEEED2; -fx-background-radius:12; -fx-padding:10;";
    private static final String SQUARE_DARK =
        "-fx-background-color:#769656; -fx-background-radius:12; -fx-padding:10;";
    private static final String HIGHLIGHT =
        "-fx-border-color:#F6F669; -fx-border-width:3; -fx-border-radius:12;";
}
