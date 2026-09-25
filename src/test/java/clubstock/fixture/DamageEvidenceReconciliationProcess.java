package clubstock.fixture;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import clubstock.ApplicationContext;

/**
 * Runs startup reconciliation in a separate JVM for the evidence race regression.
 */
public final class DamageEvidenceReconciliationProcess {
    private DamageEvidenceReconciliationProcess() {
    }

    /**
     * Verifies that the submitting process holds the OS lock, then starts reconciliation.
     *
     * @param arguments Shared application data directory.
     * @throws Exception If the lock is absent or startup fails.
     */
    public static void main(String[] arguments) throws Exception {
        Path dataDirectory = Path.of(arguments[0]);
        Path lockPath = dataDirectory.resolve("damage-evidence/.image-operations.lock");
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock()) {
            if (lock != null) {
                throw new AssertionError("Submission did not retain its inter-process image lock.");
            }
        }
        System.out.println("LOCKED");
        System.out.flush();
        ApplicationContext.create(dataDirectory);
        System.out.println("RECONCILED");
        System.out.flush();
    }
}
