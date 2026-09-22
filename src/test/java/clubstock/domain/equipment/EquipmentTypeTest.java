package clubstock.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EquipmentTypeTest {

    @Test
    void create_validType_startsUnofferedWithStableIdentity() {
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("type-1");
        EquipmentTypeName name = new EquipmentTypeName("Hockey Stick");

        EquipmentType equipmentType = EquipmentType.create(equipmentTypeId, name);

        assertSame(equipmentTypeId, equipmentType.equipmentTypeId());
        assertSame(name, equipmentType.name());
        assertFalse(equipmentType.isOffered());
    }

    @Test
    void create_nullValues_rejectBeforeCreation() {
        EquipmentTypeName name = new EquipmentTypeName("Hockey Stick");
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("type-1");

        assertThrows(IllegalArgumentException.class,
                () -> EquipmentType.create(null, name));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentType.create(equipmentTypeId, null));
    }

    @Test
    void rename_offerAndUnoffer_validOperationsUpdateOnlyMutableState() {
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("type-1");
        EquipmentTypeName originalName = new EquipmentTypeName("Hockey Stick");
        EquipmentTypeName replacementName = new EquipmentTypeName("Training Stick");
        EquipmentType equipmentType = EquipmentType.create(equipmentTypeId, originalName);

        equipmentType.rename(replacementName);
        equipmentType.offer();

        assertSame(equipmentTypeId, equipmentType.equipmentTypeId());
        assertSame(replacementName, equipmentType.name());
        assertTrue(equipmentType.isOffered());

        equipmentType.unoffer();

        assertFalse(equipmentType.isOffered());
        assertSame(replacementName, equipmentType.name());
    }

    @Test
    void rename_nullName_preservesExistingType() {
        EquipmentTypeName originalName = new EquipmentTypeName("Hockey Stick");
        EquipmentType equipmentType = EquipmentType.create(new EquipmentTypeId("type-1"),
                originalName);

        assertThrows(IllegalArgumentException.class, () -> equipmentType.rename(null));

        assertSame(originalName, equipmentType.name());
        assertFalse(equipmentType.isOffered());
    }

    @Test
    void equality_sameIdentityDifferentDetails_comparesByEquipmentTypeId() {
        EquipmentType first = EquipmentType.create(new EquipmentTypeId("type-1"),
                new EquipmentTypeName("Hockey Stick"));
        EquipmentType second = EquipmentType.create(new EquipmentTypeId("type-1"),
                new EquipmentTypeName("Helmet"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentIdentity_doesNotCompareEqual() {
        EquipmentType first = EquipmentType.create(new EquipmentTypeId("type-1"),
                new EquipmentTypeName("Hockey Stick"));
        EquipmentType second = EquipmentType.create(new EquipmentTypeId("type-2"),
                new EquipmentTypeName("Hockey Stick"));

        assertNotEquals(first, second);
    }
}
