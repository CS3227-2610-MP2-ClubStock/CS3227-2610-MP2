package clubstock.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquipmentTypeIdTest {

    @Test
    void constructor_paddedValue_trimsSurroundingWhitespace() {
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("  type-1  ");

        assertEquals("type-1", equipmentTypeId.value());
    }

    @Test
    void constructor_nullOrBlankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new EquipmentTypeId(null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentTypeId(" \t "));
    }

    @Test
    void equality_caseVariantValues_remainsCaseSensitive() {
        EquipmentTypeId upperCaseId = new EquipmentTypeId("Type-1");
        EquipmentTypeId lowerCaseId = new EquipmentTypeId("type-1");

        assertNotEquals(upperCaseId, lowerCaseId);
    }
}
