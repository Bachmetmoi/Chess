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

        Button customize = menuButton("Customize");
        customize.setOnAction(e -> onCustomize());

        Button back = secondaryButton("Back");
        back.setOnAction(e -> onBack());

        return rootBox(titleLabel("Play"), subtitleLabel("Who do you want to play against?"),
                twoPlayer, engine, customize, back);
    }

    private void onTwoPlayer() {
        navigator.showGame();
    }

    private void onEngine() {
        navigator.showEngineGame();
    }

    private void onCustomize() {
        navigator.showBoardEditor();
    }

    private void onBack() {
        navigator.showMainMenu();
    }
}
