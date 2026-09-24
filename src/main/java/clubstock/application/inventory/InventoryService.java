package clubstock.application.inventory;

import java.time.Clock;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.IdGenerator;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;

/**
 * Performs Exco-authorized management of equipment types and physical inventory.
 */
public final class InventoryService {
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final IdGenerator idGenerator;
    private final AvailabilityPolicy availabilityPolicy;
    private final Clock clock;

    /**
     * Creates the inventory service.
     *
     * @param transactionManager Shared transaction boundary.
     * @param sessionManager Authenticated principal source.
     * @param idGenerator Generator for application-owned EquipmentType IDs.
     * @param availabilityPolicy Shared transactional availability rule.
     * @param clock Clock used to record item retirement.
     */
    public InventoryService(TransactionManager transactionManager, SessionManager sessionManager,
            IdGenerator idGenerator, AvailabilityPolicy availabilityPolicy, Clock clock) {
        if (transactionManager == null || sessionManager == null || idGenerator == null
                || availabilityPolicy == null || clock == null) {
            throw new IllegalArgumentException("Inventory dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.idGenerator = idGenerator;
        this.availabilityPolicy = availabilityPolicy;
        this.clock = clock;
    }

    /**
     * Lists every equipment type with its current allocatable quantity.
     *
     * @return Exco-safe category summaries.
     */
    public List<EquipmentTypeSummary> listTypes() {
        sessionManager.requireExco();
        return transactionManager.read(unitOfWork -> unitOfWork.equipmentTypes().findAll().stream()
                .map(type -> typeSummary(unitOfWork, type))
                .toList());
    }

    /**
     * Lists every physical item with its authoritative inventory state.
     *
     * @return Exco-safe physical-item summaries.
     */
    public List<EquipmentItemSummary> listItems() {
        sessionManager.requireExco();
        return transactionManager.read(unitOfWork -> unitOfWork.equipmentItems().findAll().stream()
                .map(item -> itemSummary(unitOfWork, item))
                .toList());
    }

    /**
     * Creates a new unoffered equipment type with an application-generated UUID identity.
     *
     * @param name Proposed category display name.
     */
    public void createType(String name) {
        sessionManager.requireExco();
        EquipmentTypeName validatedName = validatedTypeName(name);
        EquipmentTypeId generatedId = generatedTypeId();
        transactionManager.write(unitOfWork -> {
            rejectDuplicateName(unitOfWork, validatedName, null);
            if (unitOfWork.equipmentTypes().findById(generatedId).isPresent()) {
                throw conflict("A unique equipment type ID could not be generated. Please try again.");
            }
            unitOfWork.equipmentTypes().insert(EquipmentType.create(generatedId, validatedName));
            return null;
        });
    }

    /**
     * Renames one equipment type without changing its identity or offered state.
     *
     * @param equipmentTypeId Existing category identity.
     * @param name Replacement display name.
     */
    public void renameType(String equipmentTypeId, String name) {
        sessionManager.requireExco();
        EquipmentTypeId validatedId = validatedTypeId(equipmentTypeId);
        EquipmentTypeName validatedName = validatedTypeName(name);
        transactionManager.write(unitOfWork -> {
            EquipmentType type = requireType(unitOfWork, validatedId);
            rejectDuplicateName(unitOfWork, validatedName, validatedId);
            type.rename(validatedName);
            unitOfWork.equipmentTypes().update(type);
            return null;
        });
    }

    /**
     * Makes an equipment type visible and requestable to Members.
     *
     * @param equipmentTypeId Existing category identity.
     */
    public void offerType(String equipmentTypeId) {
        updateOfferState(equipmentTypeId, true);
    }

    /**
     * Removes an equipment type from Member browsing and requests.
     *
     * @param equipmentTypeId Existing category identity.
     */
    public void unofferType(String equipmentTypeId) {
        updateOfferState(equipmentTypeId, false);
    }

    /**
     * Deletes an unoffered equipment type only when no record references it.
     *
     * @param equipmentTypeId Existing category identity.
     */
    public void deleteType(String equipmentTypeId) {
        sessionManager.requireExco();
        EquipmentTypeId validatedId = validatedTypeId(equipmentTypeId);
        transactionManager.write(unitOfWork -> {
            EquipmentType type = requireType(unitOfWork, validatedId);
            if (type.isOffered()) {
                throw conflict("Unoffer this equipment type before deleting it.");
            }
            if (unitOfWork.equipmentItems().existsByType(validatedId)
                    || unitOfWork.loanRequests().existsByType(validatedId)
                    || unitOfWork.loans().existsByType(validatedId)) {
                throw conflict("This equipment type is referenced and cannot be deleted.");
            }
            unitOfWork.equipmentTypes().delete(validatedId);
            return null;
        });
    }

    /**
     * Adds one physical item in the GOOD and UNAVAILABLE state.
     *
     * @param equipmentId Exco-entered immutable physical-item ID.
     * @param equipmentTypeId Existing category identity.
     */
    public void addItem(String equipmentId, String equipmentTypeId) {
        sessionManager.requireExco();
        EquipmentId validatedItemId = validatedEquipmentId(equipmentId);
        EquipmentTypeId validatedTypeId = validatedTypeId(equipmentTypeId);
        transactionManager.write(unitOfWork -> {
            requireType(unitOfWork, validatedTypeId);
            if (unitOfWork.equipmentItems().findById(validatedItemId).isPresent()) {
                throw conflict("That Equipment ID is already reserved.");
            }
            unitOfWork.equipmentItems().insert(EquipmentItem.create(validatedItemId, validatedTypeId));
            return null;
        });
    }

    /**
     * Releases an eligible active item for allocation.
     *
     * @param equipmentId Existing physical-item ID.
     */
    public void releaseItem(String equipmentId) {
        sessionManager.requireExco();
        EquipmentId validatedId = validatedEquipmentId(equipmentId);
        transactionManager.write(unitOfWork -> {
            EquipmentItem item = requireItem(unitOfWork, validatedId);
            try {
                item.release();
            } catch (IllegalStateException exception) {
                throw conflict(exception.getMessage());
            }
            unitOfWork.equipmentItems().update(item);
            return null;
        });
    }

    /**
     * Soft-retires an item only when no unresolved Loan references it.
     *
     * @param equipmentId Existing physical-item ID.
     */
    public void retireItem(String equipmentId) {
        sessionManager.requireExco();
        EquipmentId validatedId = validatedEquipmentId(equipmentId);
        transactionManager.write(unitOfWork -> {
            EquipmentItem item = requireItem(unitOfWork, validatedId);
            if (unitOfWork.loans().existsUnresolvedByEquipment(validatedId)) {
                throw conflict("This item has an unresolved loan and cannot be retired.");
            }
            try {
                item.retire(clock.instant());
            } catch (IllegalStateException exception) {
                throw conflict(exception.getMessage());
            }
            unitOfWork.equipmentItems().update(item);
            return null;
        });
    }

    private void updateOfferState(String equipmentTypeId, boolean offered) {
        sessionManager.requireExco();
        EquipmentTypeId validatedId = validatedTypeId(equipmentTypeId);
        transactionManager.write(unitOfWork -> {
            EquipmentType type = requireType(unitOfWork, validatedId);
            if (offered) {
                type.offer();
            } else {
                type.unoffer();
            }
            unitOfWork.equipmentTypes().update(type);
            return null;
        });
    }

    private EquipmentTypeId generatedTypeId() {
        try {
            return new EquipmentTypeId(idGenerator.generateId());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "A unique equipment type ID could not be generated.", exception);
        }
    }

    private EquipmentTypeSummary typeSummary(UnitOfWork unitOfWork, EquipmentType type) {
        return new EquipmentTypeSummary(type.equipmentTypeId().value(), type.name().value(),
                type.isOffered(), availabilityPolicy.countAvailable(unitOfWork,
                        type.equipmentTypeId()));
    }

    private static EquipmentItemSummary itemSummary(UnitOfWork unitOfWork, EquipmentItem item) {
        EquipmentType type = requireType(unitOfWork, item.equipmentTypeId());
        return new EquipmentItemSummary(item.equipmentId().value(), item.equipmentTypeId().value(),
                type.name().value(), item.condition().name(), item.availability().name(),
                item.isRetired());
    }

    private static void rejectDuplicateName(UnitOfWork unitOfWork, EquipmentTypeName name,
            EquipmentTypeId currentTypeId) {
        unitOfWork.equipmentTypes().findByComparisonKey(name.comparisonKey())
                .filter(existing -> !existing.equipmentTypeId().equals(currentTypeId))
                .ifPresent(existing -> {
                    throw conflict("An equipment type with that name already exists.");
                });
    }

    private static EquipmentType requireType(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId) {
        return unitOfWork.equipmentTypes().findById(equipmentTypeId).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "The selected equipment type no longer exists.", null));
    }

    private static EquipmentItem requireItem(UnitOfWork unitOfWork, EquipmentId equipmentId) {
        return unitOfWork.equipmentItems().findById(equipmentId).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "The selected equipment item no longer exists.", null));
    }

    private static EquipmentTypeId validatedTypeId(String equipmentTypeId) {
        try {
            return new EquipmentTypeId(equipmentTypeId);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static EquipmentId validatedEquipmentId(String equipmentId) {
        try {
            return new EquipmentId(equipmentId);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static EquipmentTypeName validatedTypeName(String name) {
        try {
            return new EquipmentTypeName(name);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static ApplicationException validation(String message, Throwable cause) {
        String safeMessage = message == null || message.isBlank()
                ? "Equipment details are invalid." : message;
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, safeMessage, cause);
    }

    private static ApplicationException conflict(String message) {
        String safeMessage = message == null || message.isBlank()
                ? "This inventory operation cannot be completed." : message;
        return new ApplicationException(ApplicationErrorCode.CONFLICT, safeMessage, null);
    }
}
