package boundary;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The JavaFX Application. Owns the primary Stage, creates the Navigator, and
 * shows the main menu. Launched by {@link App} rather than directly, so the
 * class started by {@code java} is not an Application subclass (which keeps
 * JavaFX runnable from the classpath, e.g. the VS Code run button).
 */
public class ChessApp extends Application {

    @Override
    public void start(Stage stage) {
        Navigator navigator = new Navigator(stage);
        navigator.showMainMenu();

        stage.setTitle("Chess");
        stage.setResizable(false);
        stage.setWidth(700);
        stage.setHeight(820);
        stage.show();
    }
}
