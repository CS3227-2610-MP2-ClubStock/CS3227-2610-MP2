package clubstock.application.inventory;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Calculates Member-visible stock from one caller-owned transactional view of inventory.
 *
 * <p>Callers must supply the UnitOfWork in which they are already reading or changing inventory.
 * The result counts only active items with {@link EquipmentAvailability#AVAILABLE}; it never
 * exposes individual Equipment IDs. This is the shared contract for Exco inventory, Member
 * catalogue, request previews, and allocation workflows.</p>
 */
public final class AvailabilityPolicy {

    /**
     * Counts active items available for allocation for one equipment type.
     *
     * @param unitOfWork Caller-owned transactional repository view.
     * @param equipmentTypeId Equipment category to count.
     * @return Number of non-retired AVAILABLE items of the type.
     */
    public int availableCount(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId) {
        if (unitOfWork == null || equipmentTypeId == null) {
            throw new IllegalArgumentException("Availability dependencies cannot be null.");
        }
        return (int) unitOfWork.equipmentItems().findByType(equipmentTypeId).stream()
                .filter(item -> !item.isRetired())
                .filter(item -> item.availability() == EquipmentAvailability.AVAILABLE)
                .count();
    }
}
