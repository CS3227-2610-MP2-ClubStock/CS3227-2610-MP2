package clubstock.domain.equipment;

/**
 * Represents an immutable identity for one physical equipment item.
 *
 * @param value Trimmed equipment identifier.
 */
public record EquipmentId(String value) {

    /**
     * Creates an equipment ID after validating and normalizing its value.
     *
     * @param value Equipment identifier to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public EquipmentId {
        value = EquipmentValidation.requireTrimmedNonBlank(value, "Equipment ID");
    }
}
