package clubstock;

import javafx.application.Application;

/**
 * Starts JavaFX from a non-{@link Application} main class for packaged execution.
 */
public final class Launcher {
    private Launcher() {
    }

    /**
     * Starts ClubStock.
     *
     * @param args Command-line arguments forwarded to JavaFX.
     */
    public static void main(String[] args) {
        Application.launch(ClubStockApplication.class, args);
    }
}
