package clubstock.application.catalog;

import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Exposes the Member-safe identity, current name, and available quantity of an offered type.
 *
 * @param equipmentTypeId Equipment category identity.
 * @param name Current display name.
 * @param availableQuantity Current available quantity.
 */
public record CatalogType(EquipmentTypeId equipmentTypeId, String name, int availableQuantity) {
    /**
     * Validates the catalogue snapshot values.
     *
     * @throws IllegalArgumentException If identity or name is null, or quantity is negative.
     */
    public CatalogType {
        if (equipmentTypeId == null || name == null) {
            throw new IllegalArgumentException("Catalogue type identity and name are required.");
        }
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative.");
        }
    }
}
