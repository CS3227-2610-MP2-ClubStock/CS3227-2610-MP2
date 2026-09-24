package clubstock.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;

class CatalogDemoSeederTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void seed_createsUsableAccount_andRepeatLeavesItUnchanged() {
        ApplicationContext context = ApplicationContext.create(temporaryDirectory);

        assertTrue(CatalogDemoSeeder.seed(context));
        Member firstSeed = readDemoMember(context);
        assertTrue(firstSeed.isActive());
        assertEquals("Demo Member", firstSeed.name());
        assertNotNull(firstSeed.passwordHash());
        assertDemoInventory(context);

        context.authentication().authenticateMember("demo-member", "demo-password".toCharArray());
        assertTrue(context.authentication().currentPrincipal().isPresent());
        context.authentication().logout();

        assertFalse(CatalogDemoSeeder.seed(context));
        Member repeatedSeed = readDemoMember(context);
        assertEquals(firstSeed.name(), repeatedSeed.name());
        assertEquals(firstSeed.passwordHash(), repeatedSeed.passwordHash());
        assertTrue(repeatedSeed.isActive());
        assertDemoInventory(context);
    }

    /**
     * Confirms the demo fixture has both offered types, the unoffered type, and released items.
     *
     * @param context Isolated application context containing the demo data.
     */
    private void assertDemoInventory(ApplicationContext context) {
        context.transactionManager().read(unitOfWork -> {
            EquipmentType availableType = unitOfWork.equipmentTypes()
                    .findById(new EquipmentTypeId("demo-type-available")).orElseThrow();
            EquipmentType zeroStockType = unitOfWork.equipmentTypes()
                    .findById(new EquipmentTypeId("demo-type-zero-stock")).orElseThrow();
            EquipmentType unofferedType = unitOfWork.equipmentTypes()
                    .findById(new EquipmentTypeId("demo-type-unoffered")).orElseThrow();
            assertTrue(availableType.isOffered());
            assertTrue(zeroStockType.isOffered());
            assertFalse(unofferedType.isOffered());
            assertEquals(3, unitOfWork.equipmentTypes().findAll().size());
            assertEquals(2, unitOfWork.equipmentItems().findAll().size());

            for (String itemId : new String[] {"demo-available-item-1", "demo-available-item-2"}) {
                EquipmentItem item = unitOfWork.equipmentItems()
                        .findById(new EquipmentId(itemId)).orElseThrow();
                assertEquals(availableType.equipmentTypeId(), item.equipmentTypeId());
                assertEquals(EquipmentAvailability.AVAILABLE, item.availability());
            }
            assertTrue(unitOfWork.equipmentItems()
                    .findByType(zeroStockType.equipmentTypeId()).isEmpty());
            return null;
        });
    }

    /**
     * Returns the demo account from the temporary database.
     *
     * @param context Isolated application context.
     * @return Persisted demo Member.
     */
    private Member readDemoMember(ApplicationContext context) {
        return context.transactionManager().read(unitOfWork -> unitOfWork.members()
                .findById(new MemberId("demo-member")).orElseThrow());
    }
}
