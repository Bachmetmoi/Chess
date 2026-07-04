package boundary;

import control.BoardSetup;
import entity.enums.Faction;
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

    public void showGame() {
        show(new GameScreen(this).getView());
    }

    /** Starts a game from a customized position with {@code firstToMove} on move. */
    public void showGame(BoardSetup setup, Faction firstToMove) {
        show(new GameScreen(this, setup, firstToMove).getView());
    }

    public void showBoardEditor() {
        show(new BoardEditorScreen(this).getView());
    }

    /** Lets the player pick a side before starting a game against the engine. */
    public void showPlayAs() {
        show(new PlayAsScreen(this).getView());
    }

    public void showEngineGame(boolean engineIsWhite) {
        show(new GameScreen(this, true, engineIsWhite).getView());
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
