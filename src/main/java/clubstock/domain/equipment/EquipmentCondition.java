package clubstock.domain.equipment;

/**
 * Represents the authoritative condition of a physical equipment item.
 */
public enum EquipmentCondition {
    /** Item is in suitable condition. */
    GOOD,
    /** Item has damage assessed by Exco. */
    DAMAGED,
    /** Item has been confirmed lost by Exco. */
    LOST
}
