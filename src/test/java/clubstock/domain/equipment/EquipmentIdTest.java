package clubstock.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquipmentIdTest {

    @Test
    void constructor_paddedValue_trimsSurroundingWhitespace() {
        EquipmentId equipmentId = new EquipmentId("  item-1  ");

        assertEquals("item-1", equipmentId.value());
    }

    @Test
    void constructor_nullOrBlankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new EquipmentId(null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentId(" \t "));
    }

    @Test
    void equality_differentValues_doesNotCompareEqual() {
        assertNotEquals(new EquipmentId("item-1"), new EquipmentId("item-2"));
    }

    @Test
    void equality_caseVariantValues_remainsCaseSensitive() {
        EquipmentId upperCaseId = new EquipmentId("Item-1");
        EquipmentId lowerCaseId = new EquipmentId("item-1");

        assertNotEquals(upperCaseId, lowerCaseId);
    }
}
