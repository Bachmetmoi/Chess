package boundary;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Base class for every full-window screen in the app. Holds a reference to the
 * {@link Navigator} so a screen's buttons can request navigation, and provides
 * shared styling helpers so all menus share one look.
 */
public abstract class Screen {
    protected static final String BG = "#312E2B";
    protected static final Color CREAM = Color.web("#EEEED2");

    protected final Navigator navigator;

    protected Screen(Navigator navigator) {
        this.navigator = navigator;
    }

    /** Builds and returns this screen's node tree. */
    public abstract Parent getView();

    // ----- shared UI helpers -----

    protected Label titleLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 44));
        l.setTextFill(CREAM);
        return l;
    }

    protected Label subtitleLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", 17));
        l.setTextFill(Color.web("#B9B6AE"));
        l.setWrapText(true);
        l.setMaxWidth(440);
        l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-text-alignment: center;");
        return l;
    }

    protected Button menuButton(String text) {
        Button b = new Button(text);
        b.setPrefWidth(280);
        b.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18));
        b.setStyle(BTN_NORMAL);
        b.setOnMouseEntered(e -> b.setStyle(BTN_HOVER));
        b.setOnMouseExited(e -> b.setStyle(BTN_NORMAL));
        return b;
    }

    protected Button secondaryButton(String text) {
        Button b = new Button(text);
        b.setPrefWidth(170);
        b.setFont(Font.font("Segoe UI", 15));
        b.setStyle(BTN_SECONDARY);
        b.setOnMouseEntered(e -> b.setStyle(BTN_SECONDARY_HOVER));
        b.setOnMouseExited(e -> b.setStyle(BTN_SECONDARY));
        return b;
    }

    protected VBox rootBox(Node... children) {
        VBox box = new VBox(20, children);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(48));
        box.setStyle("-fx-background-color:" + BG + ";");
        return box;
    }

    private static final String BTN_NORMAL =
        "-fx-background-color:#769656; -fx-text-fill:white; -fx-background-radius:10; -fx-padding:14 28;";
    private static final String BTN_HOVER =
        "-fx-background-color:#8AAE63; -fx-text-fill:white; -fx-background-radius:10; -fx-padding:14 28;";
    private static final String BTN_SECONDARY =
        "-fx-background-color:transparent; -fx-text-fill:#B9B6AE; -fx-border-color:#5A5651; "
        + "-fx-border-radius:8; -fx-background-radius:8; -fx-padding:8 18;";
    private static final String BTN_SECONDARY_HOVER =
        "-fx-background-color:#3C3935; -fx-text-fill:#EEEED2; -fx-border-color:#6E6A64; "
        + "-fx-border-radius:8; -fx-background-radius:8; -fx-padding:8 18;";
}
