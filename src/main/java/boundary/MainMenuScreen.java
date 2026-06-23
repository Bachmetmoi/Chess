package boundary;

import javafx.scene.Parent;
import javafx.scene.control.Button;

/** First screen: Play, Normal chess, About us. */
public class MainMenuScreen extends Screen {

    public MainMenuScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        Button play = menuButton("Play");
        play.setOnAction(e -> onPlay());

        Button NormalChess = menuButton("Normal chess");
        NormalChess.setOnAction(e -> onNormalChess());

        Button about = menuButton("About us");
        about.setOnAction(e -> onAbout());

        return rootBox(titleLabel("Chess"), subtitleLabel("A JavaFX chess app"),
                play, NormalChess, about);
    }

    private void onPlay() {
        navigator.showModeSelect();
    }

    private void onNormalChess() {
        navigator.showUnderDevelopment("Normal chess");
    }

    private void onAbout() {
        navigator.showAbout();
    }
}
