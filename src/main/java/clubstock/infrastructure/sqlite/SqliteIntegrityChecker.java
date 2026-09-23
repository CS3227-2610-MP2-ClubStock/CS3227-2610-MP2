package clubstock.infrastructure.sqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;

/**
 * Detects persisted cross-record states that SQLite row constraints cannot express safely.
 */
final class SqliteIntegrityChecker {

    /**
     * Checks SQLite and ClubStock integrity rules.
     *
     * @param connection Active database connection.
     */
    void check(Connection connection) {
        checkPragma(connection, "PRAGMA integrity_check", "ok");
        checkForeignKeys(connection);
        checkPersistedState(connection);
    }

    /**
     * Checks repository-wide invariants before a write transaction commits.
     *
     * @param connection Active database connection.
     */
    void checkBeforeCommit(Connection connection) {
        checkPersistedState(connection);
    }

    /**
     * Checks persisted domain and cross-record invariants.
     *
     * @param connection Active database connection.
     */
    private void checkPersistedState(Connection connection) {
        checkBooleanAndTimestampState(connection);
        checkInactiveMemberReferences(connection);
        checkRequestLoanCardinality(connection);
        checkLoanReferences(connection);
        checkLoanItemStates(connection);
        checkReportBranches(connection);
    }

    /**
     * Checks a single-value SQLite diagnostic pragma.
     *
     * @param connection Active connection.
     * @param sql Diagnostic query.
     * @param expected Expected result.
     */
    private void checkPragma(Connection connection, String sql, String expected) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next() || !expected.equalsIgnoreCase(resultSet.getString(1))) {
                throw integrityFailure("SQLite integrity check failed.");
            }
        } catch (SQLException exception) {
            throw integrityFailure("SQLite integrity check failed.", exception);
        }
    }

    /**
     * Checks all foreign-key violations.
     *
     * @param connection Active connection.
     */
    private void checkForeignKeys(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (resultSet.next()) {
                throw integrityFailure("Database contains a foreign-key violation.");
            }
        } catch (SQLException exception) {
            throw integrityFailure("Foreign-key integrity could not be checked.", exception);
        }
    }

    /**
     * Checks lifecycle flags stored in rows.
     *
     * @param connection Active connection.
     */
    private void checkBooleanAndTimestampState(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM members WHERE (is_active = 1 AND removed_at IS NOT NULL)"
                        + " OR (is_active = 0 AND removed_at IS NULL)",
                "Member removal state is inconsistent.");
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items WHERE condition = 'LOST'"
                        + " AND availability <> 'UNAVAILABLE'",
                "Lost equipment state is inconsistent.");
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items WHERE verification_pending = 1"
                        + " AND (availability <> 'UNAVAILABLE' OR is_retired = 1)",
                "Verification-pending equipment state is inconsistent.");
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items WHERE is_retired = 1"
                        + " AND (availability <> 'UNAVAILABLE' OR verification_pending = 1"
                        + " OR retired_at IS NULL)",
                "Retired equipment state is inconsistent.");
    }

    /**
     * Rejects inactive Members that still own unresolved requests or Loans.
     *
     * @param connection Active database connection.
     */
    private void checkInactiveMemberReferences(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM members m WHERE m.is_active = 0 AND ("
                        + " EXISTS (SELECT 1 FROM loan_requests r WHERE r.member_id = m.member_id"
                        + " AND r.status = 'PENDING')"
                        + " OR EXISTS (SELECT 1 FROM loans l WHERE l.member_id = m.member_id"
                        + " AND l.status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING')))",
                "Inactive Member is referenced by an unresolved request or Loan.");
    }

    /**
     * Checks approved quantities and unique item allocations for each request.
     *
     * @param connection Active database connection.
     */
    private void checkRequestLoanCardinality(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM loans GROUP BY loan_request_id, equipment_id HAVING COUNT(*) > 1",
                "An equipment item has multiple Loans for the same request.");
        assertNoRows(connection,
                "SELECT 1 FROM loan_requests r WHERE"
                        + " (r.status = 'APPROVED' AND (r.approved_quantity IS NULL OR"
                        + " (SELECT COUNT(*) FROM loans l WHERE l.loan_request_id = r.loan_request_id)"
                        + " <> r.approved_quantity))"
                        + " OR (r.status <> 'APPROVED' AND EXISTS (SELECT 1 FROM loans l"
                        + " WHERE l.loan_request_id = r.loan_request_id))",
                "Loan count does not match its approved request quantity.");
    }

    /**
     * Checks that each Loan matches its source request and item type.
     *
     * @param connection Active connection.
     */
    private void checkLoanReferences(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM loans l"
                        + " JOIN loan_requests r ON r.loan_request_id = l.loan_request_id"
                        + " JOIN equipment_items i ON i.equipment_id = l.equipment_id"
                        + " WHERE r.status <> 'APPROVED'"
                        + " OR r.member_id <> l.member_id"
                        + " OR i.equipment_type_id <> r.equipment_type_id"
                        + " OR l.end_date <> r.requested_end_date",
                "Loan references do not match its request and equipment.");
    }

    /**
     * Checks equipment availability and unresolved Loan states agree.
     *
     * @param connection Active connection.
     */
    private void checkLoanItemStates(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items i WHERE i.availability = 'ON_LOAN'"
                        + " AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.equipment_id = i.equipment_id"
                        + " AND l.status = 'ON_LOAN')",
                "On-loan equipment has no active Loan.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status = 'ON_LOAN'"
                        + " AND NOT EXISTS (SELECT 1 FROM equipment_items i"
                        + " WHERE i.equipment_id = l.equipment_id AND i.availability = 'ON_LOAN'"
                        + " AND i.verification_pending = 0 AND i.is_retired = 0)",
                "Active Loan equipment is not on loan.");
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items i WHERE i.verification_pending = 1"
                        + " AND NOT EXISTS (SELECT 1 FROM loans l WHERE l.equipment_id = i.equipment_id"
                        + " AND l.status IN ('RETURN_PENDING', 'LOST_PENDING'))",
                "Verification-pending item has no unresolved Loan.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status IN ('RETURN_PENDING', 'LOST_PENDING')"
                        + " AND NOT EXISTS (SELECT 1 FROM equipment_items i"
                        + " WHERE i.equipment_id = l.equipment_id AND i.verification_pending = 1"
                        + " AND i.availability = 'UNAVAILABLE' AND i.is_retired = 0)",
                "Unresolved Loan item is not held for verification.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l"
                        + " JOIN loss_reports d ON d.loan_id = l.loan_id"
                        + " JOIN equipment_items i ON i.equipment_id = l.equipment_id"
                        + " WHERE l.status = 'COMPLETED'"
                        + " AND l.reported_return_condition IS NULL"
                        + " AND (i.condition <> 'LOST' OR i.availability <> 'UNAVAILABLE')",
                "Completed loss item is not lost and unavailable.");
        assertNoRows(connection,
                "SELECT 1 FROM equipment_items i WHERE i.is_retired = 1"
                        + " AND EXISTS (SELECT 1 FROM loans l WHERE l.equipment_id = i.equipment_id"
                        + " AND l.status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING'))",
                "Retired equipment is referenced by an unresolved Loan.");
    }

    /**
     * Checks reports against their Loan branches.
     *
     * @param connection Active connection.
     */
    private void checkReportBranches(Connection connection) {
        assertNoRows(connection,
                "SELECT 1 FROM damage_reports d JOIN loans l ON l.loan_id = d.loan_id"
                        + " WHERE l.status NOT IN ('RETURN_PENDING', 'COMPLETED')"
                        + " OR l.reported_return_condition IS NOT 'DAMAGED'",
                "Damage report does not match its Loan branch.");
        assertNoRows(connection,
                "SELECT 1 FROM loss_reports d JOIN loans l ON l.loan_id = d.loan_id"
                        + " WHERE l.status NOT IN ('LOST_PENDING', 'COMPLETED')"
                        + " OR l.reported_return_condition IS NOT NULL",
                "Loss report does not match its Loan branch.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status = 'RETURN_PENDING'"
                        + " AND l.reported_return_condition = 'DAMAGED'"
                        + " AND NOT EXISTS (SELECT 1 FROM damage_reports d WHERE d.loan_id = l.loan_id)",
                "Damaged pending return has no damage report.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status = 'LOST_PENDING'"
                        + " AND NOT EXISTS (SELECT 1 FROM loss_reports d WHERE d.loan_id = l.loan_id)",
                "Pending loss has no loss report.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status = 'COMPLETED'"
                        + " AND l.reported_return_condition = 'DAMAGED'"
                        + " AND NOT EXISTS (SELECT 1 FROM damage_reports d WHERE d.loan_id = l.loan_id)",
                "Completed damaged return has no damage report.");
        assertNoRows(connection,
                "SELECT 1 FROM loans l WHERE l.status = 'COMPLETED'"
                        + " AND l.reported_return_condition IS NULL"
                        + " AND NOT EXISTS (SELECT 1 FROM loss_reports d WHERE d.loan_id = l.loan_id)",
                "Completed loss has no loss report.");
    }

    /**
     * Fails when a query returns any row.
     *
     * @param connection Active connection.
     * @param sql Violation query.
     * @param message Safe failure message.
     */
    private void assertNoRows(Connection connection, String sql, String message) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                throw integrityFailure(message);
            }
        } catch (SQLException exception) {
            throw integrityFailure(message, exception);
        }
    }

    /**
     * Creates a safe integrity failure.
     *
     * @param message Safe display message.
     * @return Persistence failure.
     */
    private static ApplicationException integrityFailure(String message) {
        return integrityFailure(message, null);
    }

    /**
     * Creates a safe integrity failure with a technical cause.
     *
     * @param message Safe display message.
     * @param cause Technical cause.
     * @return Persistence failure.
     */
    private static ApplicationException integrityFailure(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message, cause);
    }
}
