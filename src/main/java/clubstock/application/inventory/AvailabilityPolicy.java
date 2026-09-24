package clubstock.application.inventory;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Counts Member-visible inventory using an existing caller-owned unit of work.
 *
 * <p>Implementations count only active, {@code AVAILABLE} physical items for one type. The
 * policy never exposes Equipment IDs and must not start a nested transaction, so Member catalogue,
 * Exco inventory, request previews, and allocation workflows observe one shared state.</p>
 */
public interface AvailabilityPolicy {

    /**
     * Returns the current available quantity for one equipment type.
     *
     * @param unitOfWork Caller-owned unit of work for the current read or write operation.
     * @param equipmentTypeId Equipment category to count.
     * @return Number of active AVAILABLE physical items for the type.
     */
    int countAvailable(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId);
}
