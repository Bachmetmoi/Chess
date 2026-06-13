package boundary;

import javafx.scene.Parent;
import javafx.scene.control.Button;

/** "About us" screen reached from the main menu. */
public class AboutScreen extends Screen {

    public AboutScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        Button back = secondaryButton("Back");
        back.setOnAction(e -> onBack());

        return rootBox(
                titleLabel("About us"),
                subtitleLabel("A chess application built in Java with a JavaFX interface and an "
                        + "Entity\u2013Control\u2013Boundary architecture. It supports the full "
                        + "standard rules: castling, en passant, promotion, undo, the 50-move rule "
                        + "and threefold repetition."),
                subtitleLabel("Play vs Engine and Fun chess are on the way."),
                back);
    }

    private void onBack() {
        navigator.showMainMenu();
    }
}
