package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Persists physical equipment items.
 */
public interface EquipmentItemRepository {
    /**
     * Finds an equipment item by identity.
     *
     * @param equipmentId Equipment identity.
     * @return Matching item, when present.
     */
    Optional<EquipmentItem> findById(EquipmentId equipmentId);
    /**
     * Returns all equipment items ordered by stable identity.
     *
     * @return All equipment items.
     */
    List<EquipmentItem> findAll();
    /**
     * Finds items belonging to a type.
     *
     * @param equipmentTypeId Type identity.
     * @return Items belonging to the type.
     */
    List<EquipmentItem> findByType(EquipmentTypeId equipmentTypeId);
    /**
     * Inserts an equipment item.
     *
     * @param equipmentItem Item to insert.
     */
    void insert(EquipmentItem equipmentItem);
    /**
     * Updates an equipment item.
     *
     * @param equipmentItem Item to update.
     */
    void update(EquipmentItem equipmentItem);
    /**
     * Checks whether a type has any physical items.
     *
     * @param equipmentTypeId Type identity.
     * @return Whether at least one item references it.
     */
    boolean existsByType(EquipmentTypeId equipmentTypeId);
}
