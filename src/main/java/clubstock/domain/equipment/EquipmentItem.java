package clubstock.domain.equipment;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents one physical equipment item and its guarded lifecycle state.
 */
public final class EquipmentItem {
    private final EquipmentId equipmentId;
    private final EquipmentTypeId equipmentTypeId;
    private EquipmentCondition condition;
    private EquipmentAvailability availability;
    private boolean verificationPending;
    private boolean retired;
    private Instant retiredAt;

    private EquipmentItem(EquipmentId equipmentId, EquipmentTypeId equipmentTypeId) {
        this.equipmentId = EquipmentValidation.requireNonNull(equipmentId, "Equipment ID");
        this.equipmentTypeId = EquipmentValidation.requireNonNull(
                equipmentTypeId, "Equipment type ID");
        condition = EquipmentCondition.GOOD;
        availability = EquipmentAvailability.UNAVAILABLE;
    }

    /**
     * Creates a new physical item awaiting explicit Exco release.
     *
     * @param equipmentId Stable physical item identity.
     * @param equipmentTypeId Equipment category identity.
     * @return Newly created item in GOOD and UNAVAILABLE state.
     * @throws IllegalArgumentException If either argument is null.
     */
    public static EquipmentItem create(EquipmentId equipmentId, EquipmentTypeId equipmentTypeId)
            throws IllegalArgumentException {
        return new EquipmentItem(equipmentId, equipmentTypeId);
    }

    /**
     * Restores a physical item from persisted lifecycle state.
     *
     * @param equipmentId Stable physical item identity.
     * @param equipmentTypeId Equipment category identity.
     * @param condition Authoritative condition.
     * @param availability Current availability.
     * @param isVerificationPending Whether a Member report awaits verification.
     * @param isRetired Whether the item is soft-retired.
     * @param retiredAt Retirement instant when retired.
     * @return Restored equipment item.
     * @throws IllegalArgumentException If the state combination is invalid.
     */
    public static EquipmentItem restore(EquipmentId equipmentId, EquipmentTypeId equipmentTypeId,
            EquipmentCondition condition, EquipmentAvailability availability,
            boolean isVerificationPending, boolean isRetired, Instant retiredAt)
            throws IllegalArgumentException {
        EquipmentItem item = new EquipmentItem(equipmentId, equipmentTypeId);
        item.condition = EquipmentValidation.requireNonNull(condition, "Equipment condition");
        item.availability = EquipmentValidation.requireNonNull(availability,
                "Equipment availability");
        if (item.condition == EquipmentCondition.LOST
                && item.availability != EquipmentAvailability.UNAVAILABLE) {
            throw new IllegalArgumentException("Lost equipment must be unavailable.");
        }
        if (isVerificationPending && (item.availability != EquipmentAvailability.UNAVAILABLE
                || isRetired)) {
            throw new IllegalArgumentException(
                    "Verification-pending equipment must be unavailable and active.");
        }
        if (isRetired && (item.availability != EquipmentAvailability.UNAVAILABLE
                || isVerificationPending || retiredAt == null)) {
            throw new IllegalArgumentException("Retired equipment state is inconsistent.");
        }
        if (!isRetired && retiredAt != null) {
            throw new IllegalArgumentException("Active equipment cannot have a retirement time.");
        }

        item.verificationPending = isVerificationPending;
        item.retired = isRetired;
        item.retiredAt = retiredAt;
        return item;
    }

    /**
     * Returns this physical item's immutable identity.
     *
     * @return Physical item identity.
     */
    public EquipmentId equipmentId() {
        return equipmentId;
    }

    /**
     * Returns the equipment category assigned to this item.
     *
     * @return Equipment category identity.
     */
    public EquipmentTypeId equipmentTypeId() {
        return equipmentTypeId;
    }

    /**
     * Returns the item's authoritative condition.
     *
     * @return Current authoritative condition.
     */
    public EquipmentCondition condition() {
        return condition;
    }

    /**
     * Returns the item's current allocation availability.
     *
     * @return Current availability state.
     */
    public EquipmentAvailability availability() {
        return availability;
    }

    /**
     * Returns whether the item is awaiting Exco verification.
     *
     * @return True when a return or loss report is pending.
     */
    public boolean isVerificationPending() {
        return verificationPending;
    }

    /**
     * Returns whether the item has been soft-retired.
     *
     * @return True when the item is retired.
     */
    public boolean isRetired() {
        return retired;
    }

    /**
     * Returns the instant at which this item was retired.
     *
     * @return Optional retirement instant.
     */
    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
    }

    /**
     * Soft-retires an item that is not held by a Loan workflow.
     *
     * @param retirementInstant Retirement instant.
     * @throws IllegalArgumentException If the instant is null.
     * @throws IllegalStateException If the item is on loan, pending verification, or retired.
     */
    public void retire(Instant retirementInstant)
            throws IllegalArgumentException, IllegalStateException {
        if (retired) {
            throw new IllegalStateException("Equipment item is already retired.");
        }
        if (availability == EquipmentAvailability.ON_LOAN) {
            throw new IllegalStateException("Equipment item is on loan.");
        }
        if (verificationPending) {
            throw new IllegalStateException("Equipment item is awaiting verification.");
        }
        if (retirementInstant == null) {
            throw new IllegalArgumentException("Retirement instant cannot be null.");
        }

        availability = EquipmentAvailability.UNAVAILABLE;
        retired = true;
        retiredAt = retirementInstant;
    }

    /**
     * Releases an item for allocation after Exco determines it is suitable.
     *
     * @throws IllegalStateException If the item is not unavailable, is awaiting verification,
     *         or has been confirmed lost.
     */
    public void release() throws IllegalStateException {
        if (retired) {
            throw new IllegalStateException("A retired equipment item cannot be released.");
        }
        requireAvailability(EquipmentAvailability.UNAVAILABLE, "release");
        if (verificationPending) {
            throw new IllegalStateException("Equipment item is awaiting verification.");
        }
        if (condition == EquipmentCondition.LOST) {
            throw new IllegalStateException("A lost equipment item cannot be released.");
        }

        availability = EquipmentAvailability.AVAILABLE;
    }

    /**
     * Allocates an available item to a Loan.
     *
     * @throws IllegalStateException If the item is not currently available.
     */
    public void allocate() throws IllegalStateException {
        requireAvailability(EquipmentAvailability.AVAILABLE, "allocate");
        availability = EquipmentAvailability.ON_LOAN;
    }

    /**
     * Holds an allocated item for Exco verification after a Member return or loss report.
     *
     * @throws IllegalStateException If the item is not currently on loan.
     */
    public void holdForVerification() throws IllegalStateException {
        requireAvailability(EquipmentAvailability.ON_LOAN, "hold for verification");
        availability = EquipmentAvailability.UNAVAILABLE;
        verificationPending = true;
    }

    /**
     * Verifies a held item as good and makes it available for future allocation.
     *
     * @throws IllegalStateException If the item is not awaiting verification.
     */
    public void verifyGood() throws IllegalStateException {
        requireVerificationPending();
        condition = EquipmentCondition.GOOD;
        availability = EquipmentAvailability.AVAILABLE;
        verificationPending = false;
    }

    /**
     * Verifies a held item as damaged and applies Exco's availability decision.
     *
     * @param isAvailable Whether Exco clears the damaged item for future allocation.
     * @throws IllegalStateException If the item is not awaiting verification.
     */
    public void verifyDamaged(boolean isAvailable) throws IllegalStateException {
        requireVerificationPending();
        condition = EquipmentCondition.DAMAGED;
        availability = isAvailable
                ? EquipmentAvailability.AVAILABLE
                : EquipmentAvailability.UNAVAILABLE;
        verificationPending = false;
    }

    /**
     * Confirms a held item as lost and keeps it unavailable.
     *
     * @throws IllegalStateException If the item is not awaiting verification.
     */
    public void confirmLost() throws IllegalStateException {
        requireVerificationPending();
        condition = EquipmentCondition.LOST;
        availability = EquipmentAvailability.UNAVAILABLE;
        verificationPending = false;
    }

    /**
     * Requires an item to have the expected availability before a transition.
     *
     * @param expectedAvailability Required current availability.
     * @param operationName Operation being attempted.
     * @throws IllegalStateException If the current availability differs from the expected state.
     */
    private void requireAvailability(EquipmentAvailability expectedAvailability,
            String operationName) throws IllegalStateException {
        if (availability != expectedAvailability) {
            throw new IllegalStateException("Cannot " + operationName + " equipment item in state "
                    + availability + ".");
        }
    }

    /**
     * Requires the item to be held for Exco verification.
     *
     * @throws IllegalStateException If no return or loss report is pending.
     */
    private void requireVerificationPending() throws IllegalStateException {
        if (!verificationPending) {
            throw new IllegalStateException("Equipment item is not awaiting verification.");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof EquipmentItem equipmentItem)) {
            return false;
        }

        return equipmentId.equals(equipmentItem.equipmentId);
    }

    @Override
    public int hashCode() {
        return equipmentId.hashCode();
    }
}
