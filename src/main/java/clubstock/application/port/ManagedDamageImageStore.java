package clubstock.application.port;

import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;

import clubstock.domain.report.DamageImageReference;

/**
 * Manages staged and finalized damage images while retaining Exco read access.
 */
public interface ManagedDamageImageStore extends DamageEvidenceStore {
    /**
     * Runs an operation exclusively against this store across application processes.
     *
     * <p>Callers must hold this lock while staging, finalizing, or deleting images, and from
     * before reading committed references through completion of reconciliation.
     *
     * @param operation Operation to run while holding the exclusive lock.
     * @param <T> Operation result type.
     * @return Operation result.
     */
    <T> T withExclusiveAccess(Supplier<T> operation);

    /**
     * Validates and copies a selected JPEG or PNG into temporary managed storage.
     *
     * @param sourcePath Selected image path, which may be absolute or relative.
     * @return Opaque handle for the staged image.
     */
    StagedDamageImage stage(Path sourcePath);

    /**
     * Moves a staged image into finalized storage under a generated relative key.
     *
     * @param stagedImage Staged image handle.
     * @return Database-safe image reference for the finalized image.
     */
    DamageImageReference finalizeImage(StagedDamageImage stagedImage);

    /**
     * Discards a staged image when its submission is abandoned.
     *
     * @param stagedImage Staged image handle.
     */
    void discardStaged(StagedDamageImage stagedImage);

    /**
     * Deletes a newly finalized image after its caller has established that no committed report
     * references it.
     *
     * @param reference Finalized image reference.
     */
    void discardFinalized(DamageImageReference reference);

    /**
     * Removes abandoned staging files and unreferenced generated images.
     *
     * @param committedReferences References read from committed damage reports.
     */
    void reconcile(Collection<DamageImageReference> committedReferences);
}
