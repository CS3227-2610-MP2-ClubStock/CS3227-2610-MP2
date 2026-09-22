package clubstock.domain.equipment;

import com.ibm.icu.lang.UCharacter;

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
     * Returns a locale-independent Unicode case-folded key for case-insensitive uniqueness checks.
     *
     * @return Locale-independent Unicode case-folded comparison key.
     */
    public String comparisonKey() {
        return UCharacter.foldCase(value, UCharacter.FOLD_CASE_DEFAULT);
    }
}
