package boundary;

import javafx.scene.Parent;
import javafx.scene.control.Button;

/**
 * Shared placeholder shown for features that aren't ready. The {@code feature}
 * label makes the message contextual, so the same screen serves both "Fun
 * chess" and "Play vs Engine".
 */
public class UnderDevelopmentScreen extends Screen {
    private final String feature;

    public UnderDevelopmentScreen(Navigator navigator, String feature) {
        super(navigator);
        this.feature = feature;
    }

    @Override
    public Parent getView() {
        Button back = secondaryButton("Back to menu");
        back.setOnAction(e -> onBack());

        return rootBox(
                titleLabel(feature),
                subtitleLabel("Under development"),
                subtitleLabel("This feature isn't ready yet \u2014 check back soon."),
                back);
    }

    private void onBack() {
        navigator.showMainMenu();
    }
}
