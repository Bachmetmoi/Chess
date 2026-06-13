import boundary.ChessApp;
import javafx.application.Application;

/**
 * Plain launcher (NOT an Application subclass). Running this with `java App`
 * avoids the launcher's "JavaFX runtime components are missing" check, so
 * JavaFX loads from the classpath (VS Code run button, exec:java). The real
 * Application lives in {@link ChessApp}.
 */
public class App {
    public static void main(String[] args) {
        Application.launch(ChessApp.class, args);
    }
}
