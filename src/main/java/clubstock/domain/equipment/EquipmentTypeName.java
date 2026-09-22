package clubstock.domain.equipment;

import java.util.Locale;

/**
 * Represents an equipment type's display name and stable comparison key.
 *
 * @param value Trimmed display name.
 */
public record EquipmentTypeName(String value) {

    /**
     * Creates an equipment type name after validating and normalizing its display value.
     *
     * @param value Equipment type name to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public EquipmentTypeName {
        value = EquipmentValidation.requireTrimmedNonBlank(value, "Equipment type name");
    }

    /**
     * Returns a locale-independent lowercase key for case-insensitive uniqueness checks.
     *
     * @return Locale-independent lowercase comparison key.
     */
    public String comparisonKey() {
        return value.toLowerCase(Locale.ROOT);
    }
}
