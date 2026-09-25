package clubstock.application.verification;

/** Safe Exco summary of one advisory return or loss report awaiting verification. */
public record PendingVerification(String loanId, String memberName, String equipmentTypeName,
        String equipmentId, String reportKind, String reportedCondition, boolean hasDamageImage,
        String description) {
}
