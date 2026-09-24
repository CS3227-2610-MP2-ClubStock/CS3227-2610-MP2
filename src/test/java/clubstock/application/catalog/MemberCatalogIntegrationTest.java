package clubstock.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AccountRole;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;

class MemberCatalogIntegrationTest {
    private static final MemberId MEMBER_ID = new MemberId("catalog-integration-member");
    private static final String MEMBER_PASSWORD = "catalog-member-password";
    private static final EquipmentTypeId COURT_BALLS_ID = new EquipmentTypeId("court-balls");
    private static final EquipmentTypeId ZERO_STOCK_ID = new EquipmentTypeId("zero-stock");
    private static final EquipmentTypeId HIDDEN_TYPE_ID = new EquipmentTypeId("hidden-type");

    @TempDir
    Path temporaryDirectory;

    @Test
    void catalogue_tracksSharedInventoryAcrossRoleSwitchAndRejectsOtherRoles() {
        ApplicationContext context = ApplicationContext.create(temporaryDirectory);
        seedSharedData(context);

        ApplicationException signedOutFailure = assertThrows(ApplicationException.class,
                context.memberCatalogService()::listOfferedTypes);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED,
                signedOutFailure.errorCode());

        context.authentication().authenticateMember(MEMBER_ID.value(),
                MEMBER_PASSWORD.toCharArray());
        List<CatalogType> initialSnapshot = context.memberCatalogService().listOfferedTypes();

        CatalogType initialCourtBalls = findType(initialSnapshot, COURT_BALLS_ID);
        CatalogType initialZeroStock = findType(initialSnapshot, ZERO_STOCK_ID);
        assertEquals("Court Balls", initialCourtBalls.name());
        assertEquals(2, initialCourtBalls.availableQuantity());
        assertEquals("Zero Stock", initialZeroStock.name());
        assertEquals(0, initialZeroStock.availableQuantity());
        assertFalse(containsType(initialSnapshot, HIDDEN_TYPE_ID));
        assertEquals(List.of("Court Balls", "Zero Stock"),
                initialSnapshot.stream().map(CatalogType::name).toList());

        context.authentication().logout();
        context.authentication().completeExcoSetup("exco-integration-password".toCharArray(),
                "exco-integration-password".toCharArray());
        assertEquals(AccountRole.EXCO, context.sessionManager().requireExco().role());

        ApplicationException excoFailure = assertThrows(ApplicationException.class,
                context.memberCatalogService()::listOfferedTypes);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, excoFailure.errorCode());

        applyExcoStyleInventoryChanges(context);
        context.authentication().logout();
        context.authentication().authenticateMember(MEMBER_ID.value(),
                MEMBER_PASSWORD.toCharArray());
        List<CatalogType> refreshedSnapshot = context.memberCatalogService().listOfferedTypes();

        CatalogType refreshedCourtBalls = findType(refreshedSnapshot, COURT_BALLS_ID);
        CatalogType refreshedZeroStock = findType(refreshedSnapshot, ZERO_STOCK_ID);
        assertEquals("Court Balls Updated", refreshedCourtBalls.name());
        assertEquals(3, refreshedCourtBalls.availableQuantity());
        assertEquals(0, refreshedZeroStock.availableQuantity());
        assertFalse(containsType(refreshedSnapshot, HIDDEN_TYPE_ID));
    }

    /**
     * Seeds valid account and equipment state through the context's shared SQLite repositories.
     *
     * @param context Application context under test.
     */
    private static void seedSharedData(ApplicationContext context) {
        Member member = Member.create(MEMBER_ID, "Catalogue Integration Member",
                new Pbkdf2PasswordHasher().hash(MEMBER_PASSWORD.toCharArray()));

        EquipmentType courtBalls = offeredType(COURT_BALLS_ID, "Court Balls");
        EquipmentType zeroStock = offeredType(ZERO_STOCK_ID, "Zero Stock");
        EquipmentType hiddenType = EquipmentType.create(HIDDEN_TYPE_ID,
                new EquipmentTypeName("Hidden Type"));

        EquipmentItem availableGoodBall = EquipmentItem.create(
                new EquipmentId("court-ball-good"), COURT_BALLS_ID);
        availableGoodBall.release();
        EquipmentItem availableDamagedBall = EquipmentItem.restore(
                new EquipmentId("court-ball-damaged"), COURT_BALLS_ID,
                EquipmentCondition.DAMAGED, EquipmentAvailability.AVAILABLE,
                false, false, null);
        EquipmentItem unavailableBall = EquipmentItem.create(
                new EquipmentId("court-ball-unavailable"), COURT_BALLS_ID);
        EquipmentItem retiredBall = EquipmentItem.create(
                new EquipmentId("court-ball-retired"), COURT_BALLS_ID);
        retiredBall.retire(Instant.parse("2026-09-24T00:00:00Z"));
        EquipmentItem zeroStockItem = EquipmentItem.create(
                new EquipmentId("zero-stock-unavailable"), ZERO_STOCK_ID);
        EquipmentItem hiddenAvailableItem = EquipmentItem.create(
                new EquipmentId("hidden-type-available"), HIDDEN_TYPE_ID);
        hiddenAvailableItem.release();

        context.transactionManager().write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(courtBalls);
            unitOfWork.equipmentTypes().insert(zeroStock);
            unitOfWork.equipmentTypes().insert(hiddenType);
            unitOfWork.equipmentItems().insert(availableGoodBall);
            unitOfWork.equipmentItems().insert(availableDamagedBall);
            unitOfWork.equipmentItems().insert(unavailableBall);
            unitOfWork.equipmentItems().insert(retiredBall);
            unitOfWork.equipmentItems().insert(zeroStockItem);
            unitOfWork.equipmentItems().insert(hiddenAvailableItem);
            return null;
        });
    }

    /**
     * Changes inventory and the offered type's name through shared domain and repository APIs.
     *
     * @param context Application context with the Exco principal active.
     */
    private static void applyExcoStyleInventoryChanges(ApplicationContext context) {
        context.transactionManager().write(unitOfWork -> {
            EquipmentId unavailableBallId = new EquipmentId("court-ball-unavailable");
            EquipmentItem unavailableBall = unitOfWork.equipmentItems()
                    .findById(unavailableBallId).orElseThrow();
            unavailableBall.release();
            unitOfWork.equipmentItems().update(unavailableBall);

            EquipmentType courtBalls = unitOfWork.equipmentTypes()
                    .findById(COURT_BALLS_ID).orElseThrow();
            courtBalls.rename(new EquipmentTypeName("Court Balls Updated"));
            unitOfWork.equipmentTypes().update(courtBalls);
            return null;
        });
    }

    /**
     * Creates an offered equipment type for the fixture.
     *
     * @param equipmentTypeId Stable fixture identity.
     * @param name Display name.
     * @return Offered equipment type.
     */
    private static EquipmentType offeredType(EquipmentTypeId equipmentTypeId, String name) {
        EquipmentType equipmentType = EquipmentType.create(equipmentTypeId,
                new EquipmentTypeName(name));
        equipmentType.offer();
        return equipmentType;
    }

    /**
     * Finds one type in the returned catalogue snapshot.
     *
     * @param snapshot Catalogue snapshot.
     * @param equipmentTypeId Type identity to find.
     * @return Matching catalogue type.
     */
    private static CatalogType findType(List<CatalogType> snapshot,
            EquipmentTypeId equipmentTypeId) {
        return snapshot.stream()
                .filter(type -> type.equipmentTypeId().equals(equipmentTypeId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns whether a type appears in a Member catalogue snapshot.
     *
     * @param snapshot Catalogue snapshot.
     * @param equipmentTypeId Type identity to find.
     * @return Whether the type is present.
     */
    private static boolean containsType(List<CatalogType> snapshot,
            EquipmentTypeId equipmentTypeId) {
        return snapshot.stream().anyMatch(type -> type.equipmentTypeId().equals(equipmentTypeId));
    }
}
