package clubstock.domain.equipment;

/**
 * Represents an immutable identity for an equipment category.
 *
 * @param value Trimmed equipment type identifier.
 */
public record EquipmentTypeId(String value) {

    /**
     * Creates an equipment type ID after validating and normalizing its value.
     *
     * @param value Equipment type identifier to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public EquipmentTypeId {
        value = EquipmentValidation.requireTrimmedNonBlank(value, "Equipment type ID");
    }
}
