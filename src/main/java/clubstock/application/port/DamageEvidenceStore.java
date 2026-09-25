package clubstock.application.port;

import java.util.Optional;

import clubstock.application.verification.DamageEvidence;
import clubstock.domain.report.DamageImageReference;

/**
 * Retrieves damage evidence through application-managed storage keys.
 */
public interface DamageEvidenceStore {
    /**
     * Retrieves the bytes for one managed image reference.
     *
     * @param reference Validated opaque image reference.
     * @return Evidence when the referenced managed image is still available.
     */
    Optional<DamageEvidence> find(DamageImageReference reference);
}
