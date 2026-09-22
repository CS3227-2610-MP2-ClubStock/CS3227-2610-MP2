package clubstock.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquipmentTypeNameTest {

    @Test
    void constructor_paddedValue_trimsDisplayName() {
        EquipmentTypeName name = new EquipmentTypeName("  Hockey Stick  ");

        assertEquals("Hockey Stick", name.value());
    }

    @Test
    void constructor_nullOrBlankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new EquipmentTypeName(null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentTypeName(" \t "));
    }

    @Test
    void comparisonKey_caseVariantsShareLocaleIndependentKey() {
        EquipmentTypeName upperCaseName = new EquipmentTypeName("Hockey Stick");
        EquipmentTypeName lowerCaseName = new EquipmentTypeName("hockey stick");

        assertEquals("hockey stick", upperCaseName.comparisonKey());
        assertEquals(upperCaseName.comparisonKey(), lowerCaseName.comparisonKey());
    }
}
