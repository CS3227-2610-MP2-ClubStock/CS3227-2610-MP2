package clubstock;

import javafx.application.Application;

/**
 * Starts JavaFX from a non-{@link Application} main class for packaged execution.
 */
public final class Launcher {
    static final int VERIFICATION_NOT_REQUESTED = -1;
    private static final String VERIFY_INSTALL_ARGUMENT = "--verify-install";

    private Launcher() {
    }

    /**
     * Starts ClubStock.
     *
     * @param args Command-line arguments forwarded to JavaFX except for the standalone
     *             {@code --verify-install} verification command.
     */
    public static void main(String[] args) {
        int verificationResult = verifyInstall(args, new PackagedInstallVerifier());
        if (verificationResult != VERIFICATION_NOT_REQUESTED) {
            System.exit(verificationResult);
            return;
        }
        Application.launch(ClubStockApplication.class, args);
    }

    /**
     * Handles the standalone noninteractive verification command.
     *
     * @param args Command-line arguments.
     * @param verifier Packaged-install verifier.
     * @return A process exit status, or {@link #VERIFICATION_NOT_REQUESTED} for normal launch.
     */
    static int verifyInstall(String[] args, PackagedInstallVerifier verifier) {
        if (args == null || args.length == 0 || !VERIFY_INSTALL_ARGUMENT.equals(args[0])) {
            return VERIFICATION_NOT_REQUESTED;
        }
        if (args.length != 1) {
            System.err.println("--verify-install must be used without additional arguments.");
            return PackagedInstallVerifier.FAILURE;
        }
        return verifier.verify();
    }
}
