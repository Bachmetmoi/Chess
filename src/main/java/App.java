import boundary.UI;
import javafx.application.Application;

public class App {
    public static void main(String[] args) {
        // Launch the JavaFX board UI. The UI builds its own GameController and
        // BoardSetup, then drives the game through clicks on the board.
        Application.launch(UI.class, args);
    }
}
