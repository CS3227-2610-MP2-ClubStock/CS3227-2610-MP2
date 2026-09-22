package clubstock.domain.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquipmentItemTest {

    @Test
    void create_validItem_startsGoodAndUnavailable() {
        EquipmentId equipmentId = new EquipmentId("item-1");
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("type-1");

        EquipmentItem item = EquipmentItem.create(equipmentId, equipmentTypeId);

        assertSame(equipmentId, item.equipmentId());
        assertSame(equipmentTypeId, item.equipmentTypeId());
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());
    }

    @Test
    void create_nullValues_rejectBeforeCreation() {
        EquipmentTypeId equipmentTypeId = new EquipmentTypeId("type-1");
        EquipmentId equipmentId = new EquipmentId("item-1");

        assertThrows(IllegalArgumentException.class,
                () -> EquipmentItem.create(null, equipmentTypeId));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentItem.create(equipmentId, null));
    }

    @Test
    void releaseAllocateAndGoodVerification_followExpectedLifecycle() {
        EquipmentItem item = createItem("item-1");

        item.release();
        assertEquals(EquipmentAvailability.AVAILABLE, item.availability());

        item.allocate();
        assertEquals(EquipmentAvailability.ON_LOAN, item.availability());

        item.holdForVerification();
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());
        assertEquals(EquipmentCondition.GOOD, item.condition());

        item.verifyGood();
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.AVAILABLE, item.availability());
    }

    @Test
    void verifyDamaged_availableChoiceRepresentsBothOutcomes() {
        EquipmentItem availableItem = createItem("item-1");
        availableItem.release();
        availableItem.allocate();
        availableItem.holdForVerification();

        availableItem.verifyDamaged(true);

        assertEquals(EquipmentCondition.DAMAGED, availableItem.condition());
        assertEquals(EquipmentAvailability.AVAILABLE, availableItem.availability());

        EquipmentItem unavailableItem = createItem("item-2");
        unavailableItem.release();
        unavailableItem.allocate();
        unavailableItem.holdForVerification();

        unavailableItem.verifyDamaged(false);

        assertEquals(EquipmentCondition.DAMAGED, unavailableItem.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, unavailableItem.availability());
    }

    @Test
    void confirmLost_afterVerificationHold_keepsItemUnavailableAndUnreleasable() {
        EquipmentItem item = createItem("item-1");
        item.release();
        item.allocate();
        item.holdForVerification();

        item.confirmLost();

        assertEquals(EquipmentCondition.LOST, item.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());
        assertThrows(IllegalStateException.class, item::release);
        assertEquals(EquipmentCondition.LOST, item.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());
    }

    @Test
    void invalidTransitions_preserveState() {
        EquipmentItem item = createItem("item-1");

        assertThrows(IllegalStateException.class, item::allocate);
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());

        item.release();
        item.allocate();
        assertThrows(IllegalStateException.class, item::allocate);
        assertEquals(EquipmentAvailability.ON_LOAN, item.availability());

        assertThrows(IllegalStateException.class, item::verifyGood);
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.ON_LOAN, item.availability());

        item.holdForVerification();
        assertThrows(IllegalStateException.class, item::release);
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());

        item.verifyGood();
        assertThrows(IllegalStateException.class, item::confirmLost);
        assertEquals(EquipmentCondition.GOOD, item.condition());
        assertEquals(EquipmentAvailability.AVAILABLE, item.availability());
    }

    @Test
    void equality_sameEquipmentIdDifferentType_comparesByEquipmentId() {
        EquipmentItem first = EquipmentItem.create(new EquipmentId("item-1"),
                new EquipmentTypeId("type-1"));
        EquipmentItem second = EquipmentItem.create(new EquipmentId("item-1"),
                new EquipmentTypeId("type-2"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.equipmentId(), second.equipmentId());
    }

    @Test
    void equality_differentEquipmentId_doesNotCompareEqual() {
        EquipmentItem first = createItem("item-1");
        EquipmentItem second = createItem("item-2");

        assertNotEquals(first, second);
    }

    private EquipmentItem createItem(String equipmentId) {
        return EquipmentItem.create(new EquipmentId(equipmentId), new EquipmentTypeId("type-1"));
    }
}
