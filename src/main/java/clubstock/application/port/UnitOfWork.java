package clubstock.application.port;

/**
 * Exposes repositories bound to one active database connection.
 */
public interface UnitOfWork {

    /**
     * Returns the singleton Exco account repository.
     *
     * @return Singleton Exco account repository.
     */
    ExcoAccountRepository excoAccounts();

    /**
     * Returns the Member repository.
     *
     * @return Member repository.
     */
    MemberRepository members();

    /**
     * Returns the equipment type repository.
     *
     * @return Equipment type repository.
     */
    EquipmentTypeRepository equipmentTypes();

    /**
     * Returns the physical equipment repository.
     *
     * @return Physical equipment repository.
     */
    EquipmentItemRepository equipmentItems();

    /**
     * Returns the LoanRequest repository.
     *
     * @return LoanRequest repository.
     */
    LoanRequestRepository loanRequests();

    /**
     * Returns the Loan repository.
     *
     * @return Loan repository.
     */
    LoanRepository loans();

    /**
     * Returns the damage report repository.
     *
     * @return Damage report repository.
     */
    DamageReportRepository damageReports();

    /**
     * Returns the loss report repository.
     *
     * @return Loss report repository.
     */
    LossReportRepository lossReports();
}
