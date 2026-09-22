package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;

/**
 * Persists LoanRequests and exposes foundational reference checks.
 */
public interface LoanRequestRepository {
    /**
     * Finds a request by identity.
     *
     * @param loanRequestId Request identity.
     * @return Matching request, when present.
     */
    Optional<LoanRequest> findById(LoanRequestId loanRequestId);
    /**
     * Returns all requests ordered by stable identity.
     *
     * @return All requests.
     */
    List<LoanRequest> findAll();
    /**
     * Inserts a request.
     *
     * @param loanRequest Request to insert.
     */
    void insert(LoanRequest loanRequest);
    /**
     * Updates a request.
     *
     * @param loanRequest Request to update.
     */
    void update(LoanRequest loanRequest);
    /**
     * Checks whether any request references a type.
     *
     * @param equipmentTypeId Type identity.
     * @return Whether a request references it.
     */
    boolean existsByType(EquipmentTypeId equipmentTypeId);
    /**
     * Checks whether a pending request references a Member.
     *
     * @param memberId Member identity.
     * @return Whether a pending request references it.
     */
    boolean existsPendingByMember(MemberId memberId);
}
