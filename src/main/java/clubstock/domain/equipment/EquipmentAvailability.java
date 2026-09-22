package clubstock.domain.equipment;

/**
 * Represents whether a physical equipment item may be allocated.
 */
public enum EquipmentAvailability {
    /** Item is cleared for allocation. */
    AVAILABLE,
    /** Item is assigned to a Member through a Loan. */
    ON_LOAN,
    /** Item must not currently be allocated. */
    UNAVAILABLE
}
