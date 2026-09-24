package clubstock.application.inventory;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Counts available physical items using the caller's repository transaction.
 */
public final class EquipmentItemAvailabilityPolicy implements AvailabilityPolicy {
    /**
     * Creates the shared equipment-item availability policy.
     */
    public EquipmentItemAvailabilityPolicy() {
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException If either query argument is null.
     */
    @Override
    public int countAvailable(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId) {
        if (unitOfWork == null || equipmentTypeId == null) {
            throw new IllegalArgumentException("Availability query details cannot be null.");
        }

        int availableCount = 0;
        for (EquipmentItem item : unitOfWork.equipmentItems().findByType(equipmentTypeId)) {
            if (!item.isRetired() && item.availability() == EquipmentAvailability.AVAILABLE) {
                availableCount++;
            }
        }
        return availableCount;
    }
}
