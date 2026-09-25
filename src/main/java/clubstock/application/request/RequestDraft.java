package clubstock.application.request;

import java.time.LocalDate;

import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Holds the Member-entered values for a LoanRequest before service validation.
 *
 * @param equipmentTypeId Requested equipment type.
 * @param quantity Requested quantity.
 * @param startDate Informational requested start date.
 * @param endDate Requested end date.
 * @param details Optional request details, or {@code null} when absent.
 */
public record RequestDraft(EquipmentTypeId equipmentTypeId, int quantity, LocalDate startDate,
        LocalDate endDate, String details) {
}
