package clubstock.application.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class EquipmentItemAvailabilityPolicyTest {
    @Test
    void countAvailable_countsAvailableNonRetiredItemsWithoutConditionFiltering(
            @TempDir Path temporaryDirectory) {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentTypeId offeredTypeId = new EquipmentTypeId("type-offered");
        EquipmentTypeId otherTypeId = new EquipmentTypeId("type-other");
        EquipmentType offeredType = EquipmentType.create(offeredTypeId,
                new EquipmentTypeName("Offered Type"));
        EquipmentType otherType = EquipmentType.create(otherTypeId,
                new EquipmentTypeName("Other Type"));
        EquipmentItem availableGood = EquipmentItem.create(new EquipmentId("item-good"),
                offeredTypeId);
        availableGood.release();
        EquipmentItem availableDamaged = EquipmentItem.restore(new EquipmentId("item-damaged"),
                offeredTypeId, EquipmentCondition.DAMAGED, EquipmentAvailability.AVAILABLE,
                false, false, null);
        EquipmentItem unavailable = EquipmentItem.create(new EquipmentId("item-unavailable"),
                offeredTypeId);
        EquipmentItem retired = EquipmentItem.create(new EquipmentId("item-retired"),
                offeredTypeId);
        retired.retire(Instant.parse("2026-09-24T00:00:00Z"));
        EquipmentItem otherAvailable = EquipmentItem.create(new EquipmentId("item-other"),
                otherTypeId);
        otherAvailable.release();
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().insert(offeredType);
            unitOfWork.equipmentTypes().insert(otherType);
            unitOfWork.equipmentItems().insert(availableGood);
            unitOfWork.equipmentItems().insert(availableDamaged);
            unitOfWork.equipmentItems().insert(unavailable);
            unitOfWork.equipmentItems().insert(retired);
            unitOfWork.equipmentItems().insert(otherAvailable);
            return null;
        });
        EquipmentItemAvailabilityPolicy policy = new EquipmentItemAvailabilityPolicy();

        int offeredTypeQuantity = database.read(unitOfWork ->
                policy.countAvailable(unitOfWork, offeredTypeId));
        int otherTypeQuantity = database.read(unitOfWork ->
                policy.countAvailable(unitOfWork, otherTypeId));
        int emptyTypeQuantity = database.read(unitOfWork ->
                policy.countAvailable(unitOfWork, new EquipmentTypeId("type-empty")));

        assertEquals(2, offeredTypeQuantity);
        assertEquals(1, otherTypeQuantity);
        assertEquals(0, emptyTypeQuantity);
    }

    @Test
    void countAvailable_rejectsMissingQueryDetails() {
        EquipmentItemAvailabilityPolicy policy = new EquipmentItemAvailabilityPolicy();

        assertThrows(IllegalArgumentException.class,
                () -> policy.countAvailable(null, new EquipmentTypeId("type-1")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.countAvailable(unusedUnitOfWork(), null));
    }

    /**
     * Creates a nonnull unit of work that fails if the policy attempts repository access.
     *
     * @return Test unit of work proxy.
     */
    private static UnitOfWork unusedUnitOfWork() {
        return (UnitOfWork) Proxy.newProxyInstance(UnitOfWork.class.getClassLoader(),
                new Class<?>[] {UnitOfWork.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Invalid query must fail before repository access.");
                });
    }
}
