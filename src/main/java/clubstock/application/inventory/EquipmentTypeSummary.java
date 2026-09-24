package clubstock.application.inventory;

/**
 * Exco-safe presentation data for one equipment category.
 *
 * @param equipmentTypeId Immutable category identity.
 * @param name Display name.
 * @param offered Whether Members can browse and request the category.
 * @param availableQuantity Current count of active AVAILABLE items.
 */
public record EquipmentTypeSummary(String equipmentTypeId, String name, boolean offered,
        int availableQuantity) {

    /**
     * Validates a safe inventory summary.
     */
    public EquipmentTypeSummary {
        if (equipmentTypeId == null || equipmentTypeId.isBlank() || name == null || name.isBlank()
                || availableQuantity < 0) {
            throw new IllegalArgumentException("Equipment type summary is invalid.");
        }
    }
}
