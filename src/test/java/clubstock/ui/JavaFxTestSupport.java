package clubstock.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/** Starts the process-wide JavaFX toolkit once for display-tagged tests. */
public final class JavaFxTestSupport {
    private static final Object STARTUP_LOCK = new Object();
    private static boolean started;

    private JavaFxTestSupport() {
    }

    /**
     * Starts the JavaFX toolkit, or waits until an already-starting toolkit is ready.
     *
     * @throws InterruptedException If the test thread is interrupted while waiting.
     */
    public static void start() throws InterruptedException {
        synchronized (STARTUP_LOCK) {
            if (started) {
                return;
            }

            CountDownLatch ready = new CountDownLatch(1);
            try {
                Platform.startup(ready::countDown);
            } catch (IllegalStateException exception) {
                Platform.runLater(ready::countDown);
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("JavaFX toolkit did not start.");
            }
            Platform.setImplicitExit(false);
            started = true;
        }
    }
}
