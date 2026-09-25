package clubstock.infrastructure.file;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import clubstock.application.port.DamageEvidenceStore;
import clubstock.application.verification.DamageEvidence;
import clubstock.domain.report.DamageImageReference;

/**
 * Reads damage images from one application-owned directory.
 */
public final class FileDamageEvidenceStore implements DamageEvidenceStore {
    private final Path root;

    /**
     * Creates a store rooted at an application-managed directory.
     *
     * @param root Root directory for managed damage evidence.
     */
    public FileDamageEvidenceStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Damage evidence directory cannot be null.");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Damage evidence directory could not be created.", exception);
        }
    }

    @Override
    public Optional<DamageEvidence> find(DamageImageReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException("Damage image reference cannot be null.");
        }
        Path image = root.resolve(reference.storageKey()).normalize();
        if (!image.startsWith(root)) {
            throw new IllegalArgumentException("Damage image reference is outside managed storage.");
        }
        if (!Files.isRegularFile(image)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(image);
            if (bytes.length != reference.sizeBytes()) {
                return Optional.empty();
            }
            return Optional.of(new DamageEvidence(bytes, reference.format()));
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Damage evidence could not be read.", exception);
        }
    }
}
