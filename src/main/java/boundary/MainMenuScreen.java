package boundary;

import javafx.scene.Parent;
import javafx.scene.control.Button;

/** First screen: Play, Fun chess, About us. */
public class MainMenuScreen extends Screen {

    public MainMenuScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        Button play = menuButton("Play");
        play.setOnAction(e -> onPlay());

        Button funChess = menuButton("Fun chess");
        funChess.setOnAction(e -> onFunChess());

        Button about = menuButton("About us");
        about.setOnAction(e -> onAbout());

        return rootBox(titleLabel("Chess"), subtitleLabel("A JavaFX chess app"),
                play, funChess, about);
    }

    private void onPlay() {
        navigator.showModeSelect();
    }

    private void onFunChess() {
        navigator.showUnderDevelopment("Fun chess");
    }

    private void onAbout() {
        navigator.showAbout();
    }
}
