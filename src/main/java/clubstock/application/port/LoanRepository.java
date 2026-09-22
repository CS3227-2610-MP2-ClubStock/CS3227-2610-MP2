package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;

/**
 * Persists individual Loans and exposes foundational reference checks.
 */
public interface LoanRepository {
    /**
     * Finds a Loan by identity.
     *
     * @param loanId Loan identity.
     * @return Matching Loan, when present.
     */
    Optional<Loan> findById(LoanId loanId);
    /**
     * Returns all Loans ordered by stable identity.
     *
     * @return All Loans.
     */
    List<Loan> findAll();
    /**
     * Inserts a Loan.
     *
     * @param loan Loan to insert.
     */
    void insert(Loan loan);
    /**
     * Updates a Loan.
     *
     * @param loan Loan to update.
     */
    void update(Loan loan);
    /**
     * Checks whether a Loan references an item of a type.
     *
     * @param equipmentTypeId Type identity.
     * @return Whether a Loan references the type.
     */
    boolean existsByType(EquipmentTypeId equipmentTypeId);
    /**
     * Checks whether an unresolved Loan references a Member.
     *
     * @param memberId Member identity.
     * @return Whether an unresolved Loan references the Member.
     */
    boolean existsUnresolvedByMember(MemberId memberId);
    /**
     * Checks whether an unresolved Loan references an item.
     *
     * @param equipmentId Equipment identity.
     * @return Whether an unresolved Loan references the item.
     */
    boolean existsUnresolvedByEquipment(EquipmentId equipmentId);
}
