package clubstock.fixture;

import java.nio.file.Path;

import clubstock.ApplicationContext;
import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;

/**
 * Creates an isolated Member account for exercising the development catalogue.
 */
public final class CatalogDemoSeeder {
    private static final Path DEMO_DATA_DIRECTORY = Path.of("build", "clubstock-demo");
    private static final MemberId DEMO_MEMBER_ID = new MemberId("demo-member");
    private static final String DEMO_PASSWORD = "demo-password";
    private static final EquipmentTypeId DEMO_AVAILABLE_TYPE_ID =
            new EquipmentTypeId("demo-type-available");
    private static final EquipmentTypeId DEMO_ZERO_STOCK_TYPE_ID =
            new EquipmentTypeId("demo-type-zero-stock");
    private static final EquipmentTypeId DEMO_UNOFFERED_TYPE_ID =
            new EquipmentTypeId("demo-type-unoffered");
    private static final EquipmentId DEMO_AVAILABLE_ITEM_ONE_ID =
            new EquipmentId("demo-available-item-1");
    private static final EquipmentId DEMO_AVAILABLE_ITEM_TWO_ID =
            new EquipmentId("demo-available-item-2");

    private CatalogDemoSeeder() {
    }

    /**
     * Seeds the disposable catalogue database without printing account credentials.
     *
     * @param arguments Command-line arguments, which are ignored.
     */
    public static void main(String[] arguments) {
        ApplicationContext context = ApplicationContext.create(DEMO_DATA_DIRECTORY);
        seed(context);
        System.out.println("The temporary catalogue Member account is ready.");
    }

    /**
     * Inserts the demo Member or verifies that the existing account can use the fixture login.
     *
     * @param context Application context that owns the isolated database.
     * @return True if the Member was inserted; false if it already existed.
     */
    static boolean seed(ApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Application context cannot be null.");
        }

        return context.transactionManager().write(unitOfWork -> {
            boolean isMemberInserted = insertMemberIfAbsent(unitOfWork);
            seedDemoInventory(unitOfWork);
            return isMemberInserted;
        });
    }

    /**
     * Creates the active demo Member or rejects stale fixture credentials.
     *
     * @param unitOfWork Active database unit of work.
     * @return True if the Member was inserted; false if it already existed.
     */
    private static boolean insertMemberIfAbsent(UnitOfWork unitOfWork) {
        Member existingMember = unitOfWork.members().findById(DEMO_MEMBER_ID).orElse(null);
        if (existingMember != null) {
            if (!existingMember.isActive()) {
                throw staleDemoAccount();
            }
            boolean hasExpectedPassword = new Pbkdf2PasswordHasher().matches(
                    DEMO_PASSWORD.toCharArray(), existingMember.passwordHash());
            if (!hasExpectedPassword) {
                throw staleDemoAccount();
            }
            return false;
        }

        PasswordHash passwordHash = new Pbkdf2PasswordHasher().hash(DEMO_PASSWORD.toCharArray());
        Member member = Member.create(DEMO_MEMBER_ID, "Demo Member", passwordHash);
        unitOfWork.members().insert(member);
        return true;
    }

    /**
     * Returns an actionable failure for an existing account that cannot use the demo login.
     *
     * @return Fixture mismatch failure.
     */
    private static ApplicationException staleDemoAccount() {
        return new ApplicationException(ApplicationErrorCode.CONFLICT,
                "The demo Member account no longer matches its fixture credentials."
                        + " Remove build/clubstock-demo and seed it again.", null);
    }

    /**
     * Adds stable demo type and item records without replacing existing rows.
     *
     * @param unitOfWork Active database unit of work.
     */
    private static void seedDemoInventory(UnitOfWork unitOfWork) {
        insertTypeIfAbsent(unitOfWork, DEMO_AVAILABLE_TYPE_ID, "Demo Available Equipment", true);
        insertTypeIfAbsent(unitOfWork, DEMO_ZERO_STOCK_TYPE_ID, "Demo Zero Stock", true);
        insertTypeIfAbsent(unitOfWork, DEMO_UNOFFERED_TYPE_ID, "Demo Unoffered Equipment", false);
        insertAvailableItemIfAbsent(unitOfWork, DEMO_AVAILABLE_ITEM_ONE_ID,
                DEMO_AVAILABLE_TYPE_ID);
        insertAvailableItemIfAbsent(unitOfWork, DEMO_AVAILABLE_ITEM_TWO_ID,
                DEMO_AVAILABLE_TYPE_ID);
    }

    /**
     * Creates a stable type fixture only when its identity is absent.
     *
     * @param unitOfWork Active database unit of work.
     * @param equipmentTypeId Stable demo type identity.
     * @param name Fixture display name.
     * @param isOffered Whether Members can browse this fixture type.
     */
    private static void insertTypeIfAbsent(UnitOfWork unitOfWork,
            EquipmentTypeId equipmentTypeId, String name, boolean isOffered) {
        if (unitOfWork.equipmentTypes().findById(equipmentTypeId).isPresent()) {
            return;
        }

        EquipmentType type = EquipmentType.create(equipmentTypeId, new EquipmentTypeName(name));
        if (isOffered) {
            type.offer();
        }
        unitOfWork.equipmentTypes().insert(type);
    }

    /**
     * Creates a released physical item fixture only when its identity is absent.
     *
     * @param unitOfWork Active database unit of work.
     * @param equipmentId Stable demo item identity.
     * @param equipmentTypeId Type identity for the item.
     */
    private static void insertAvailableItemIfAbsent(UnitOfWork unitOfWork,
            EquipmentId equipmentId, EquipmentTypeId equipmentTypeId) {
        if (unitOfWork.equipmentItems().findById(equipmentId).isPresent()) {
            return;
        }

        EquipmentItem item = EquipmentItem.create(equipmentId, equipmentTypeId);
        item.release();
        unitOfWork.equipmentItems().insert(item);
    }
}
