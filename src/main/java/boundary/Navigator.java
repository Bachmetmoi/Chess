package boundary;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Owns the single {@link Stage}/{@link Scene} and switches between screens by
 * swapping the scene's root. Each show* method builds a fresh screen, so e.g.
 * starting a game always begins from a clean board.
 */
public class Navigator {
    private final Stage stage;
    private Scene scene;

    public Navigator(Stage stage) {
        this.stage = stage;
    }

    public void showMainMenu() {
        show(new MainMenuScreen(this).getView());
    }

    public void showAbout() {
        show(new AboutScreen(this).getView());
    }

    public void showModeSelect() {
        show(new ModeSelectScreen(this).getView());
    }

    public void showUnderDevelopment(String feature) {
        show(new UnderDevelopmentScreen(this, feature).getView());
    }

    public void showTrailer(String feature) {
        show(new TrailerScreen(this, feature).getView());
    }

    public void showGame() {
        show(new GameScreen(this).getView());
    }

    public void showEngineGame() {
        show(new GameScreen(this, true).getView());
    }

    private void show(Parent root) {
        if (scene == null) {
            scene = new Scene(root);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }
}
