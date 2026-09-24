package clubstock.application.inventory;

/**
 * Exco-safe presentation data for one physical equipment item.
 *
 * @param equipmentId Immutable physical-item identity.
 * @param equipmentTypeId Owning category identity.
 * @param equipmentTypeName Owning category display name.
 * @param condition Authoritative condition label.
 * @param availability Allocation availability label.
 * @param retired Whether the item is retired from active inventory.
 */
public record EquipmentItemSummary(String equipmentId, String equipmentTypeId,
        String equipmentTypeName, String condition, String availability, boolean retired) {

    /**
     * Validates a safe inventory summary.
     */
    public EquipmentItemSummary {
        if (equipmentId == null || equipmentId.isBlank() || equipmentTypeId == null
                || equipmentTypeId.isBlank() || equipmentTypeName == null
                || equipmentTypeName.isBlank() || condition == null || condition.isBlank()
                || availability == null || availability.isBlank()) {
            throw new IllegalArgumentException("Equipment item summary is invalid.");
        }
    }
}
