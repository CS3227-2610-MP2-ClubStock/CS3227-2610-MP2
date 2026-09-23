package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Persists equipment categories.
 */
public interface EquipmentTypeRepository {
    /**
     * Finds an equipment type by identity.
     *
     * @param equipmentTypeId Equipment type identity.
     * @return Matching type, when present.
     */
    Optional<EquipmentType> findById(EquipmentTypeId equipmentTypeId);
    /**
     * Finds an equipment type by folded name key.
     *
     * @param comparisonKey Folded name key.
     * @return Matching type, when present.
     */
    Optional<EquipmentType> findByComparisonKey(String comparisonKey);
    /**
     * Returns all equipment types ordered by stable identity.
     *
     * @return All equipment types.
     */
    List<EquipmentType> findAll();
    /**
     * Inserts an equipment type.
     *
     * @param equipmentType Type to insert.
     */
    void insert(EquipmentType equipmentType);
    /**
     * Updates an equipment type.
     *
     * @param equipmentType Type to update.
     */
    void update(EquipmentType equipmentType);
    /**
     * Deletes an unoffered equipment type when no record references it.
     *
     * @param equipmentTypeId Type identity to delete.
     */
    void delete(EquipmentTypeId equipmentTypeId);
}
