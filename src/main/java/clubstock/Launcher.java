package clubstock;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
     * Starts ClubStock or runs isolated installation verification.
     *
     * @param args Command-line arguments forwarded to JavaFX for normal startup.
     */
    public static void main(String[] args) {
        int mode = verificationMode(args);
        if (mode == 0) {
            Application.launch(ClubStockApplication.class, args);
            return;
        }
        if (mode == 2) {
            System.err.println("Usage: java -jar ClubStock-<version>-<platform>.jar --verify-install");
            System.exit(2);
            return;
        }
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "installation-verification-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.schedule(() -> {
            System.err.println("Installation verification timed out after 60 seconds; temporary files may remain.");
            System.exit(3);
        }, 60, TimeUnit.SECONDS);
        int result = 1;
        try {
            System.out.println("Checking JavaFX initialization");
            Application.launch(InstallVerificationApplication.class, args);
            result = InstallVerificationApplication.exitCode();
        } catch (Exception | LinkageError exception) {
            System.err.println("Installation verification failed during JavaFX startup or shutdown");
            exception.printStackTrace(System.err);
        } finally {
            watchdog.shutdownNow();
        }
        if (result == 0) {
            System.out.println("INSTALL_VERIFICATION_OK");
        }
        System.exit(result);
    }

    /**
     * Returns 0 for normal startup, 1 for verification, or 2 for invalid verification arguments.
     */
    static int verificationMode(String[] args) {
        if (!Arrays.asList(args).contains("--verify-install")) {
            return 0;
        }
        return args.length == 1 ? 1 : 2;
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
