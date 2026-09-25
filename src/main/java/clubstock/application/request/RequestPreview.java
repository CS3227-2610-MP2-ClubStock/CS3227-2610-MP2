package clubstock.application.request;

import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Shows the offered type, current available quantity, and validated request draft.
 *
 * @param equipmentTypeId Requested equipment type identity.
 * @param equipmentTypeName Current equipment type name.
 * @param availableQuantity Current available item count.
 * @param draft Validated draft with absent details normalized to {@code null}.
 */
public record RequestPreview(EquipmentTypeId equipmentTypeId, String equipmentTypeName,
        int availableQuantity, RequestDraft draft) {

    /**
     * Validates the preview snapshot values.
     *
     * @throws IllegalArgumentException If the type identity, name, or draft is null, if the
     *         available quantity is negative, or if the draft identifies a different type.
     */
    public RequestPreview {
        if (equipmentTypeId == null || equipmentTypeName == null || draft == null) {
            throw new IllegalArgumentException("Request preview details are required.");
        }
        if (equipmentTypeName.isBlank() || availableQuantity < 0
                || !equipmentTypeId.equals(draft.equipmentTypeId())) {
            throw new IllegalArgumentException("Request preview details are invalid.");
        }
    }
}
