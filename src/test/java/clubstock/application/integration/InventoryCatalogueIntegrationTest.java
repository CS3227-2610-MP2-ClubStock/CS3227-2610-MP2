package clubstock.application.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.application.catalog.CatalogType;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;

class InventoryCatalogueIntegrationTest {
    private static final MemberId MEMBER_ID = new MemberId("inventory-catalogue-member");
    private static final String MEMBER_PASSWORD = "catalogue-member-password";
    private static final String EXCO_PASSWORD = "inventory-exco-password";

    @TempDir
    Path temporaryDirectory;

    @Test
    void excoInventoryLifecycleUpdatesTheSharedMemberCatalogue() {
        ApplicationContext context = ApplicationContext.create(temporaryDirectory);
        context.transactionManager().write(unitOfWork -> {
            unitOfWork.members().insert(Member.create(MEMBER_ID, "Inventory catalogue Member",
                    new Pbkdf2PasswordHasher().hash(MEMBER_PASSWORD.toCharArray())));
            return null;
        });

        context.authentication().completeExcoSetup(EXCO_PASSWORD.toCharArray(),
                EXCO_PASSWORD.toCharArray());
        context.inventoryService().createType("Court balls");
        String typeId = context.inventoryService().listTypes().getFirst().equipmentTypeId();
        context.inventoryService().offerType(typeId);
        context.inventoryService().addItem("court-ball-01", typeId);

        context.authentication().logout();
        context.authentication().authenticateMember(MEMBER_ID.value(), MEMBER_PASSWORD.toCharArray());
        CatalogType beforeRelease = context.memberCatalogService().listOfferedTypes().getFirst();
        assertEquals("Court balls", beforeRelease.name());
        assertEquals(0, beforeRelease.availableQuantity());

        context.authentication().logout();
        context.authentication().authenticateExco(EXCO_PASSWORD.toCharArray());
        context.inventoryService().releaseItem("court-ball-01");

        context.authentication().logout();
        context.authentication().authenticateMember(MEMBER_ID.value(), MEMBER_PASSWORD.toCharArray());
        CatalogType afterRelease = context.memberCatalogService().listOfferedTypes().getFirst();
        assertEquals(1, afterRelease.availableQuantity());

        context.authentication().logout();
        context.authentication().authenticateExco(EXCO_PASSWORD.toCharArray());
        context.inventoryService().retireItem("court-ball-01");

        context.authentication().logout();
        context.authentication().authenticateMember(MEMBER_ID.value(), MEMBER_PASSWORD.toCharArray());
        CatalogType afterRetirement = context.memberCatalogService().listOfferedTypes().getFirst();
        assertEquals(0, afterRetirement.availableQuantity());
        assertEquals(typeId, afterRetirement.equipmentTypeId().value());
    }
}
