package clubstock.application.inventory;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Counts inventory available for allocation using an existing unit of work.
 *
 * <p>Counts only non-retired items whose availability is {@code AVAILABLE}, regardless of their
 * condition. Implementations use the supplied unit of work and do not start a nested transaction.
 */
public interface AvailabilityPolicy {
    /**
     * Returns the current available quantity for one equipment type.
     *
     * @param unitOfWork Caller-owned unit of work for the current read or write operation.
     * @param equipmentTypeId Equipment category to count.
     * @return Number of available physical items for the type.
     */
    int countAvailable(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId);
}
