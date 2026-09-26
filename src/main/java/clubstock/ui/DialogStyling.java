package clubstock.ui;

import java.net.URL;

import javafx.scene.control.Dialog;

/** Applies the application's shared stylesheet to a separate JavaFX dialog scene. */
public final class DialogStyling {
    private DialogStyling() {
    }

    public static void apply(Dialog<?> dialog) {
        URL stylesheet = DialogStyling.class.getResource("/clubstock/ui/clubstock.css");
        if (stylesheet == null) {
            throw new IllegalStateException("ClubStock stylesheet is missing.");
        }
        dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
    }
}
