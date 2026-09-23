package clubstock.domain.equipment;

/**
 * Represents an equipment category that can be offered for Member requests.
 */
public final class EquipmentType {
    private final EquipmentTypeId equipmentTypeId;
    private EquipmentTypeName name;
    private boolean offered;

    private EquipmentType(EquipmentTypeId equipmentTypeId, EquipmentTypeName name) {
        this.equipmentTypeId = EquipmentValidation.requireNonNull(
                equipmentTypeId, "Equipment type ID");
        this.name = EquipmentValidation.requireNonNull(name, "Equipment type name");
    }

    /**
     * Creates an equipment type that is initially not offered to Members.
     *
     * @param equipmentTypeId Stable equipment category identity.
     * @param name Display name for the equipment category.
     * @return Newly created, unoffered equipment type.
     * @throws IllegalArgumentException If either argument is null.
     */
    public static EquipmentType create(EquipmentTypeId equipmentTypeId, EquipmentTypeName name)
            throws IllegalArgumentException {
        return new EquipmentType(equipmentTypeId, name);
    }

    /**
     * Restores an equipment type from persisted state.
     *
     * @param equipmentTypeId Stable equipment category identity.
     * @param name Persisted display name.
     * @param isOffered Whether Members can browse and request the type.
     * @return Restored equipment type.
     */
    public static EquipmentType restore(EquipmentTypeId equipmentTypeId, EquipmentTypeName name,
            boolean isOffered) {
        EquipmentType type = new EquipmentType(equipmentTypeId, name);
        type.offered = isOffered;
        return type;
    }

    /**
     * Returns this equipment type's immutable identity.
     *
     * @return Equipment category identity.
     */
    public EquipmentTypeId equipmentTypeId() {
        return equipmentTypeId;
    }

    /**
     * Returns this equipment type's current display name.
     *
     * @return Equipment category display name.
     */
    public EquipmentTypeName name() {
        return name;
    }

    /**
     * Returns whether this equipment type is offered for Member browsing and requests.
     *
     * @return True when the type is offered.
     */
    public boolean isOffered() {
        return offered;
    }

    /**
     * Replaces this equipment type's display name.
     *
     * @param name Replacement display name.
     * @throws IllegalArgumentException If the name is null.
     */
    public void rename(EquipmentTypeName name) throws IllegalArgumentException {
        this.name = EquipmentValidation.requireNonNull(name, "Equipment type name");
    }

    /**
     * Marks this equipment type as offered for Member browsing and requests.
     */
    public void offer() {
        offered = true;
    }

    /**
     * Marks this equipment type as unavailable for new Member browsing and requests.
     */
    public void unoffer() {
        offered = false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof EquipmentType equipmentType)) {
            return false;
        }

        return equipmentTypeId.equals(equipmentType.equipmentTypeId);
    }

    @Override
    public int hashCode() {
        return equipmentTypeId.hashCode();
    }
}
