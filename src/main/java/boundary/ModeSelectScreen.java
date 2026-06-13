package boundary;

import javafx.scene.Parent;
import javafx.scene.control.Button;

/** Reached from "Play": choose 2 Player (launches the board) or Engine. */
public class ModeSelectScreen extends Screen {

    public ModeSelectScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        Button twoPlayer = menuButton("2 Player");
        twoPlayer.setOnAction(e -> onTwoPlayer());

        Button engine = menuButton("Engine");
        engine.setOnAction(e -> onEngine());

        Button back = secondaryButton("Back");
        back.setOnAction(e -> onBack());

        return rootBox(titleLabel("Play"), subtitleLabel("Who do you want to play against?"),
                twoPlayer, engine, back);
    }

    private void onTwoPlayer() {
        navigator.showGame();
    }

    private void onEngine() {
        navigator.showUnderDevelopment("Play vs Engine");
    }

    private void onBack() {
        navigator.showMainMenu();
    }
}
