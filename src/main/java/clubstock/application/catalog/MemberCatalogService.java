package clubstock.application.catalog;

import java.util.Comparator;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentType;

/**
 * Reads the current Member-facing snapshot of offered equipment types.
 */
public final class MemberCatalogService {
    /**
     * Safe failure used when a Member session no longer identifies an active account.
     */
    private static final String MEMBER_UNAVAILABLE_MESSAGE =
            "This operation is not available for the current session.";

    /**
     * Shared read boundary for catalogue and inventory state.
     */
    private final TransactionManager transactionManager;
    /**
     * Current role and authenticated Member identity.
     */
    private final SessionManager sessionManager;
    /**
     * Shared policy for counting allocatable physical inventory.
     */
    private final AvailabilityPolicy availabilityPolicy;

    /**
     * Creates the Member catalogue query service.
     *
     * @param transactionManager Shared database transaction boundary.
     * @param sessionManager Current application session.
     * @param availabilityPolicy Shared inventory availability policy.
     * @throws IllegalArgumentException If any dependency is null.
     */
    public MemberCatalogService(TransactionManager transactionManager,
            SessionManager sessionManager, AvailabilityPolicy availabilityPolicy) {
        if (transactionManager == null || sessionManager == null || availabilityPolicy == null) {
            throw new IllegalArgumentException("Member catalogue dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.availabilityPolicy = availabilityPolicy;
    }

    /**
     * Returns a complete, immutable snapshot of offered types for the authenticated Member.
     *
     * @return Offered type names, identities, and available quantities in stable order.
     */
    public List<CatalogType> listOfferedTypes() {
        MemberId memberId = sessionManager.requireMember();
        return transactionManager.read(unitOfWork -> listOfferedTypes(unitOfWork, memberId));
    }

    /**
     * Reads offered types after checking that the authenticated account remains active.
     *
     * @param unitOfWork Transaction-scoped repositories and shared state.
     * @param memberId Authenticated Member identity.
     * @return Immutable catalogue snapshot.
     */
    private List<CatalogType> listOfferedTypes(UnitOfWork unitOfWork, MemberId memberId) {
        sessionManager.requireMember(memberId);
        Member member = unitOfWork.members().findById(memberId)
                .orElseThrow(MemberCatalogService::memberUnavailable);
        if (!member.isActive()) {
            throw memberUnavailable();
        }

        Comparator<EquipmentType> typeOrder = Comparator
                .comparing((EquipmentType type) -> type.name().comparisonKey())
                .thenComparing(type -> type.equipmentTypeId().value());
        List<CatalogType> snapshot = unitOfWork.equipmentTypes().findAll().stream()
                .filter(EquipmentType::isOffered)
                .sorted(typeOrder)
                .map(type -> new CatalogType(type.equipmentTypeId(), type.name().value(),
                        availabilityPolicy.countAvailable(unitOfWork, type.equipmentTypeId())))
                .toList();
        return List.copyOf(snapshot);
    }

    /**
     * Creates the safe denial used when the signed-in Member account is absent or inactive.
     *
     * @return Authorization failure.
     */
    private static ApplicationException memberUnavailable() {
        return new ApplicationException(ApplicationErrorCode.AUTHORIZATION_DENIED,
                MEMBER_UNAVAILABLE_MESSAGE, null);
    }
}
