package clubstock.infrastructure.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.port.DamageReportRepository;
import clubstock.application.port.EquipmentItemRepository;
import clubstock.application.port.EquipmentTypeRepository;
import clubstock.application.port.ExcoAccountRepository;
import clubstock.application.port.LoanRepository;
import clubstock.application.port.LoanRequestRepository;
import clubstock.application.port.LossReportRepository;
import clubstock.application.port.MemberRepository;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.ExcoAccount;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.loan.ReportedReturnCondition;
import clubstock.domain.report.DamageImageFormat;
import clubstock.domain.report.DamageImageReference;
import clubstock.domain.report.DamageReport;
import clubstock.domain.report.LossReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

/**
 * Provides repositories bound to one JDBC connection.
 */
final class SqliteUnitOfWork implements UnitOfWork {
    private final ExcoAccounts excoAccounts;
    private final Members members;
    private final EquipmentTypes equipmentTypes;
    private final EquipmentItems equipmentItems;
    private final LoanRequests loanRequests;
    private final Loans loans;
    private final DamageReports damageReports;
    private final LossReports lossReports;

    /**
     * Creates repositories bound to the supplied connection.
     *
     * @param connection Active SQLite connection.
     */
    SqliteUnitOfWork(Connection connection) {
        excoAccounts = new ExcoAccounts(connection);
        members = new Members(connection);
        equipmentTypes = new EquipmentTypes(connection);
        equipmentItems = new EquipmentItems(connection);
        loanRequests = new LoanRequests(connection);
        loans = new Loans(connection);
        damageReports = new DamageReports(connection);
        lossReports = new LossReports(connection);
    }

    @Override
    public ExcoAccountRepository excoAccounts() {
        return excoAccounts;
    }

    @Override
    public MemberRepository members() {
        return members;
    }

    @Override
    public EquipmentTypeRepository equipmentTypes() {
        return equipmentTypes;
    }

    @Override
    public EquipmentItemRepository equipmentItems() {
        return equipmentItems;
    }

    @Override
    public LoanRequestRepository loanRequests() {
        return loanRequests;
    }

    @Override
    public LoanRepository loans() {
        return loans;
    }

    @Override
    public DamageReportRepository damageReports() {
        return damageReports;
    }

    @Override
    public LossReportRepository lossReports() {
        return lossReports;
    }

    /**
     * Loads every persisted entity to validate its stored representation.
     */
    void validateStoredRows() {
        excoAccounts.get();
        members.findAll();
        equipmentTypes.findAll();
        equipmentItems.findAll();
        loanRequests.findAll();
        loans.findAll();
        damageReports.findAll();
        lossReports.findAll();
    }

    private abstract static class RepositorySupport {
        protected final Connection connection;

        RepositorySupport(Connection connection) {
            this.connection = connection;
        }

        protected ApplicationException persistenceFailure(String message, Throwable cause) {
            return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message,
                    cause);
        }

        protected ApplicationException mapSqlFailure(String message, SQLException exception) {
            String sqlMessage = exception.getMessage();
            ApplicationErrorCode code = sqlMessage != null && sqlMessage.toLowerCase(Locale.ROOT)
                    .contains("constraint")
                    ? ApplicationErrorCode.CONFLICT
                    : ApplicationErrorCode.PERSISTENCE_FAILURE;
            return new ApplicationException(code, message, exception);
        }

        protected void requireUpdated(int count, String entityName) {
            if (count == 0) {
                throw new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        entityName + " was not found.", null);
            }
        }

        protected <T> Optional<T> optional(ResultSet resultSet, RowMapper<T> mapper) {
            try {
                return resultSet.next() ? Optional.of(mapper.map(resultSet)) : Optional.empty();
            } catch (SQLException exception) {
                throw persistenceFailure("Stored data could not be read.", exception);
            } catch (ApplicationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw persistenceFailure("Stored data could not be read.", exception);
            }
        }

        protected <T> List<T> list(ResultSet resultSet, RowMapper<T> mapper) {
            List<T> values = new ArrayList<>();
            try {
                while (resultSet.next()) {
                    values.add(mapper.map(resultSet));
                }
                return values;
            } catch (SQLException exception) {
                throw persistenceFailure("Stored data could not be read.", exception);
            } catch (ApplicationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw persistenceFailure("Stored data could not be read.", exception);
            }
        }

        protected boolean exists(String sql, String value) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Stored reference could not be checked.", exception);
            }
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private static final class ExcoAccounts extends RepositorySupport
            implements ExcoAccountRepository {
        ExcoAccounts(Connection connection) {
            super(connection);
        }

        @Override
        public ExcoAccount get() {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT password_hash FROM exco_account WHERE singleton_id = 1")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw persistenceFailure("Singleton Exco account is missing.", null);
                    }
                    String encodedHash = resultSet.getString(1);
                    return ExcoAccount.restore(encodedHash == null
                            ? Optional.empty()
                            : Optional.of(new PasswordHash(encodedHash)));
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Exco account could not be read.", exception);
            } catch (RuntimeException exception) {
                throw persistenceFailure("Exco account could not be read.", exception);
            }
        }

        @Override
        public void save(ExcoAccount account) {
            if (account == null) {
                throw new IllegalArgumentException("Exco account cannot be null.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE exco_account SET password_hash = ? WHERE singleton_id = 1")) {
                statement.setString(1, account.passwordHash().map(PasswordHash::encodedHash)
                        .orElse(null));
                requireUpdated(statement.executeUpdate(), "Exco account");
            } catch (SQLException exception) {
                throw mapSqlFailure("Exco account could not be saved.", exception);
            }
        }
    }

    private static final class Members extends RepositorySupport implements MemberRepository {
        Members(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<Member> findById(MemberId memberId) {
            requireId(memberId, "Member ID");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT member_id, name, password_hash, is_active, removed_at"
                            + " FROM members WHERE member_id = ?")) {
                statement.setString(1, memberId.value());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapMember);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Member could not be read.", exception);
            }
        }

        @Override
        public List<Member> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT member_id, name, password_hash, is_active, removed_at"
                                    + " FROM members ORDER BY member_id")) {
                return list(resultSet, SqliteUnitOfWork::mapMember);
            } catch (SQLException exception) {
                throw persistenceFailure("Members could not be read.", exception);
            }
        }

        @Override
        public void insert(Member member) {
            requireEntity(member, "Member");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO members(member_id, name, password_hash, is_active, removed_at)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, member.memberId().value());
                statement.setString(2, member.name());
                statement.setString(3, member.passwordHash().encodedHash());
                statement.setInt(4, member.isActive() ? 1 : 0);
                statement.setString(5, member.removedAt().map(Instant::toString).orElse(null));
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Member could not be inserted.", exception);
            }
        }

        @Override
        public void update(Member member) {
            requireEntity(member, "Member");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE members SET name = ?, password_hash = ?, is_active = ?, removed_at = ?"
                            + " WHERE member_id = ?")) {
                statement.setString(1, member.name());
                statement.setString(2, member.passwordHash().encodedHash());
                statement.setInt(3, member.isActive() ? 1 : 0);
                statement.setString(4, member.removedAt().map(Instant::toString).orElse(null));
                statement.setString(5, member.memberId().value());
                requireUpdated(statement.executeUpdate(), "Member");
            } catch (SQLException exception) {
                throw mapSqlFailure("Member could not be updated.", exception);
            }
        }
    }

    private static final class EquipmentTypes extends RepositorySupport
            implements EquipmentTypeRepository {
        EquipmentTypes(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<EquipmentType> findById(EquipmentTypeId equipmentTypeId) {
            requireId(equipmentTypeId, "Equipment type ID");
            return find("WHERE equipment_type_id = ?", equipmentTypeId.value());
        }

        @Override
        public Optional<EquipmentType> findByComparisonKey(String comparisonKey) {
            if (comparisonKey == null || comparisonKey.isBlank()) {
                throw new IllegalArgumentException("Equipment type comparison key cannot be blank.");
            }
            return find("WHERE comparison_key = ?", comparisonKey);
        }

        @Override
        public List<EquipmentType> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(typeSelect("ORDER BY equipment_type_id"))) {
                return list(resultSet, SqliteUnitOfWork::mapEquipmentType);
            } catch (SQLException exception) {
                throw persistenceFailure("Equipment types could not be read.", exception);
            }
        }

        @Override
        public void insert(EquipmentType type) {
            requireEntity(type, "Equipment type");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO equipment_types(equipment_type_id, name, comparison_key, is_offered)"
                            + " VALUES (?, ?, ?, ?)")) {
                statement.setString(1, type.equipmentTypeId().value());
                statement.setString(2, type.name().value());
                statement.setString(3, type.name().comparisonKey());
                statement.setInt(4, type.isOffered() ? 1 : 0);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Equipment type could not be inserted.", exception);
            }
        }

        @Override
        public void update(EquipmentType type) {
            requireEntity(type, "Equipment type");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE equipment_types SET name = ?, comparison_key = ?, is_offered = ?"
                            + " WHERE equipment_type_id = ?")) {
                statement.setString(1, type.name().value());
                statement.setString(2, type.name().comparisonKey());
                statement.setInt(3, type.isOffered() ? 1 : 0);
                statement.setString(4, type.equipmentTypeId().value());
                requireUpdated(statement.executeUpdate(), "Equipment type");
            } catch (SQLException exception) {
                throw mapSqlFailure("Equipment type could not be updated.", exception);
            }
        }

        @Override
        public void delete(EquipmentTypeId equipmentTypeId) {
            requireId(equipmentTypeId, "Equipment type ID");
            EquipmentType type = findById(equipmentTypeId).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "Equipment type was not found.", null));
            if (type.isOffered()) {
                throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                        "An offered equipment type cannot be deleted.", null);
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM equipment_types WHERE equipment_type_id = ?")) {
                statement.setString(1, equipmentTypeId.value());
                requireUpdated(statement.executeUpdate(), "Equipment type");
            } catch (SQLException exception) {
                throw mapSqlFailure("Equipment type could not be deleted.", exception);
            }
        }

        private Optional<EquipmentType> find(String where, String value) {
            try (PreparedStatement statement = connection.prepareStatement(typeSelect(where))) {
                statement.setString(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapEquipmentType);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Equipment type could not be read.", exception);
            }
        }

        private static String typeSelect(String suffix) {
            return "SELECT equipment_type_id, name, comparison_key, is_offered"
                    + " FROM equipment_types " + suffix;
        }
    }

    private static final class EquipmentItems extends RepositorySupport
            implements EquipmentItemRepository {
        EquipmentItems(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<EquipmentItem> findById(EquipmentId equipmentId) {
            requireId(equipmentId, "Equipment ID");
            return find("WHERE equipment_id = ?", equipmentId.value());
        }

        @Override
        public List<EquipmentItem> findAll() {
            return findList("ORDER BY equipment_id", null);
        }

        @Override
        public List<EquipmentItem> findByType(EquipmentTypeId equipmentTypeId) {
            requireId(equipmentTypeId, "Equipment type ID");
            return findList("WHERE equipment_type_id = ? ORDER BY equipment_id",
                    equipmentTypeId.value());
        }

        @Override
        public void insert(EquipmentItem item) {
            requireEntity(item, "Equipment item");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO equipment_items(equipment_id, equipment_type_id, condition,"
                            + " availability, verification_pending, is_retired, retired_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                bindItem(statement, item, false);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Equipment item could not be inserted.", exception);
            }
        }

        @Override
        public void update(EquipmentItem item) {
            requireEntity(item, "Equipment item");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE equipment_items SET equipment_type_id = ?, condition = ?,"
                            + " availability = ?, verification_pending = ?, is_retired = ?, retired_at = ?"
                            + " WHERE equipment_id = ?")) {
                statement.setString(1, item.equipmentTypeId().value());
                statement.setString(2, item.condition().name());
                statement.setString(3, item.availability().name());
                statement.setInt(4, item.isVerificationPending() ? 1 : 0);
                statement.setInt(5, item.isRetired() ? 1 : 0);
                statement.setString(6, item.retiredAt().map(Instant::toString).orElse(null));
                statement.setString(7, item.equipmentId().value());
                requireUpdated(statement.executeUpdate(), "Equipment item");
            } catch (SQLException exception) {
                throw mapSqlFailure("Equipment item could not be updated.", exception);
            }
        }

        @Override
        public boolean existsByType(EquipmentTypeId equipmentTypeId) {
            requireId(equipmentTypeId, "Equipment type ID");
            return exists("SELECT 1 FROM equipment_items WHERE equipment_type_id = ?",
                    equipmentTypeId.value());
        }

        private Optional<EquipmentItem> find(String suffix, String value) {
            try (PreparedStatement statement = connection.prepareStatement(itemSelect(suffix))) {
                if (value != null) {
                    statement.setString(1, value);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapEquipmentItem);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Equipment item could not be read.", exception);
            }
        }

        private List<EquipmentItem> findList(String suffix, String value) {
            try (PreparedStatement statement = connection.prepareStatement(itemSelect(suffix))) {
                if (value != null) {
                    statement.setString(1, value);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    return list(resultSet, SqliteUnitOfWork::mapEquipmentItem);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Equipment items could not be read.", exception);
            }
        }

        private static String itemSelect(String suffix) {
            return "SELECT equipment_id, equipment_type_id, condition, availability,"
                    + " verification_pending, is_retired, retired_at FROM equipment_items " + suffix;
        }

        private static void bindItem(PreparedStatement statement, EquipmentItem item,
                boolean update) throws SQLException {
            statement.setString(1, item.equipmentId().value());
            statement.setString(2, item.equipmentTypeId().value());
            statement.setString(3, item.condition().name());
            statement.setString(4, item.availability().name());
            statement.setInt(5, item.isVerificationPending() ? 1 : 0);
            statement.setInt(6, item.isRetired() ? 1 : 0);
            statement.setString(7, item.retiredAt().map(Instant::toString).orElse(null));
            if (update) {
                statement.setString(8, item.equipmentId().value());
            }
        }
    }

    private static final class LoanRequests extends RepositorySupport
            implements LoanRequestRepository {
        LoanRequests(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<LoanRequest> findById(LoanRequestId requestId) {
            requireId(requestId, "Loan request ID");
            return find("WHERE loan_request_id = ?", requestId.value());
        }

        @Override
        public List<LoanRequest> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(requestSelect("ORDER BY loan_request_id"))) {
                return list(resultSet, SqliteUnitOfWork::mapLoanRequest);
            } catch (SQLException exception) {
                throw persistenceFailure("Loan requests could not be read.", exception);
            }
        }

        @Override
        public void insert(LoanRequest request) {
            requireEntity(request, "Loan request");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO loan_requests(loan_request_id, member_id, equipment_type_id,"
                            + " requested_quantity, requested_start_date, requested_end_date, details,"
                            + " requested_at, status, approved_quantity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindRequest(statement, request);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Loan request could not be inserted.", exception);
            }
        }

        @Override
        public void update(LoanRequest request) {
            requireEntity(request, "Loan request");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE loan_requests SET member_id = ?, equipment_type_id = ?,"
                            + " requested_quantity = ?, requested_start_date = ?, requested_end_date = ?,"
                            + " details = ?, requested_at = ?, status = ?, approved_quantity = ?"
                            + " WHERE loan_request_id = ?")) {
                statement.setString(1, request.memberId().value());
                statement.setString(2, request.equipmentTypeId().value());
                statement.setInt(3, request.requestedQuantity());
                statement.setString(4, request.requestedStartDate().toString());
                statement.setString(5, request.requestedEndDate().toString());
                statement.setString(6, request.details().orElse(null));
                statement.setString(7, request.requestedAt().toString());
                statement.setString(8, request.status().name());
                if (request.approvedQuantity().isPresent()) {
                    statement.setInt(9, request.approvedQuantity().getAsInt());
                } else {
                    statement.setObject(9, null);
                }
                statement.setString(10, request.loanRequestId().value());
                requireUpdated(statement.executeUpdate(), "Loan request");
            } catch (SQLException exception) {
                throw mapSqlFailure("Loan request could not be updated.", exception);
            }
        }

        @Override
        public boolean existsByType(EquipmentTypeId typeId) {
            requireId(typeId, "Equipment type ID");
            return exists("SELECT 1 FROM loan_requests WHERE equipment_type_id = ?", typeId.value());
        }

        @Override
        public boolean existsPendingByMember(MemberId memberId) {
            requireId(memberId, "Member ID");
            return exists("SELECT 1 FROM loan_requests WHERE member_id = ? AND status = 'PENDING'",
                    memberId.value());
        }

        private Optional<LoanRequest> find(String suffix, String value) {
            try (PreparedStatement statement = connection.prepareStatement(requestSelect(suffix))) {
                statement.setString(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapLoanRequest);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Loan request could not be read.", exception);
            }
        }

        private static String requestSelect(String suffix) {
            return "SELECT loan_request_id, member_id, equipment_type_id, requested_quantity,"
                    + " requested_start_date, requested_end_date, details, requested_at, status,"
                    + " approved_quantity FROM loan_requests " + suffix;
        }

        private static void bindRequest(PreparedStatement statement, LoanRequest request)
                throws SQLException {
            statement.setString(1, request.loanRequestId().value());
            statement.setString(2, request.memberId().value());
            statement.setString(3, request.equipmentTypeId().value());
            statement.setInt(4, request.requestedQuantity());
            statement.setString(5, request.requestedStartDate().toString());
            statement.setString(6, request.requestedEndDate().toString());
            statement.setString(7, request.details().orElse(null));
            statement.setString(8, request.requestedAt().toString());
            statement.setString(9, request.status().name());
            if (request.approvedQuantity().isPresent()) {
                statement.setInt(10, request.approvedQuantity().getAsInt());
            } else {
                statement.setObject(10, null);
            }
        }
    }

    private static final class Loans extends RepositorySupport implements LoanRepository {
        Loans(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<Loan> findById(LoanId loanId) {
            requireId(loanId, "Loan ID");
            return find("WHERE loan_id = ?", loanId.value());
        }

        @Override
        public List<Loan> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(loanSelect("ORDER BY loan_id"))) {
                return list(resultSet, SqliteUnitOfWork::mapLoan);
            } catch (SQLException exception) {
                throw persistenceFailure("Loans could not be read.", exception);
            }
        }

        @Override
        public void insert(Loan loan) {
            requireEntity(loan, "Loan");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO loans(loan_id, loan_request_id, member_id, equipment_id, started_at,"
                            + " end_date, status, reported_return_condition) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindLoan(statement, loan);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Loan could not be inserted.", exception);
            }
        }

        @Override
        public void update(Loan loan) {
            requireEntity(loan, "Loan");
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE loans SET loan_request_id = ?, member_id = ?, equipment_id = ?,"
                            + " started_at = ?, end_date = ?, status = ?, reported_return_condition = ?"
                            + " WHERE loan_id = ?")) {
                statement.setString(1, loan.loanRequestId().value());
                statement.setString(2, loan.memberId().value());
                statement.setString(3, loan.equipmentId().value());
                statement.setString(4, loan.startedAt().toString());
                statement.setString(5, loan.endDate().toString());
                statement.setString(6, loan.status().name());
                statement.setString(7, loan.reportedReturnCondition().map(Enum::name).orElse(null));
                statement.setString(8, loan.loanId().value());
                requireUpdated(statement.executeUpdate(), "Loan");
            } catch (SQLException exception) {
                throw mapSqlFailure("Loan could not be updated.", exception);
            }
        }

        @Override
        public boolean existsByType(EquipmentTypeId typeId) {
            requireId(typeId, "Equipment type ID");
            return exists("SELECT 1 FROM loans l JOIN equipment_items i"
                    + " ON i.equipment_id = l.equipment_id WHERE i.equipment_type_id = ?",
                    typeId.value());
        }

        @Override
        public boolean existsUnresolvedByMember(MemberId memberId) {
            requireId(memberId, "Member ID");
            return exists("SELECT 1 FROM loans WHERE member_id = ?"
                    + " AND status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING')",
                    memberId.value());
        }

        @Override
        public boolean existsUnresolvedByEquipment(EquipmentId equipmentId) {
            requireId(equipmentId, "Equipment ID");
            return exists("SELECT 1 FROM loans WHERE equipment_id = ?"
                    + " AND status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING')",
                    equipmentId.value());
        }

        private Optional<Loan> find(String suffix, String value) {
            try (PreparedStatement statement = connection.prepareStatement(loanSelect(suffix))) {
                statement.setString(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapLoan);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Loan could not be read.", exception);
            }
        }

        private static String loanSelect(String suffix) {
            return "SELECT loan_id, loan_request_id, member_id, equipment_id, started_at, end_date,"
                    + " status, reported_return_condition FROM loans " + suffix;
        }

        private static void bindLoan(PreparedStatement statement, Loan loan) throws SQLException {
            statement.setString(1, loan.loanId().value());
            statement.setString(2, loan.loanRequestId().value());
            statement.setString(3, loan.memberId().value());
            statement.setString(4, loan.equipmentId().value());
            statement.setString(5, loan.startedAt().toString());
            statement.setString(6, loan.endDate().toString());
            statement.setString(7, loan.status().name());
            statement.setString(8, loan.reportedReturnCondition().map(Enum::name).orElse(null));
        }
    }

    private static final class DamageReports extends RepositorySupport
            implements DamageReportRepository {
        DamageReports(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<DamageReport> findByLoanId(LoanId loanId) {
            requireId(loanId, "Loan ID");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT loan_id, storage_key, image_format, size_bytes, description"
                            + " FROM damage_reports WHERE loan_id = ?")) {
                statement.setString(1, loanId.value());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapDamageReport);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Damage report could not be read.", exception);
            }
        }

        @Override
        public List<DamageReport> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT loan_id, storage_key, image_format, size_bytes, description"
                                    + " FROM damage_reports ORDER BY loan_id")) {
                return list(resultSet, SqliteUnitOfWork::mapDamageReport);
            } catch (SQLException exception) {
                throw persistenceFailure("Damage reports could not be read.", exception);
            }
        }

        @Override
        public void insert(DamageReport report) {
            requireEntity(report, "Damage report");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO damage_reports(loan_id, storage_key, image_format, size_bytes,"
                            + " description) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, report.loanId().value());
                statement.setString(2, report.imageReference().storageKey());
                statement.setString(3, report.imageReference().format().name());
                statement.setLong(4, report.imageReference().sizeBytes());
                statement.setString(5, report.description());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Damage report could not be inserted.", exception);
            }
        }
    }

    private static final class LossReports extends RepositorySupport
            implements LossReportRepository {
        LossReports(Connection connection) {
            super(connection);
        }

        @Override
        public Optional<LossReport> findByLoanId(LoanId loanId) {
            requireId(loanId, "Loan ID");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT loan_id, description FROM loss_reports WHERE loan_id = ?")) {
                statement.setString(1, loanId.value());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return optional(resultSet, SqliteUnitOfWork::mapLossReport);
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Loss report could not be read.", exception);
            }
        }

        @Override
        public List<LossReport> findAll() {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT loan_id, description FROM loss_reports ORDER BY loan_id")) {
                return list(resultSet, SqliteUnitOfWork::mapLossReport);
            } catch (SQLException exception) {
                throw persistenceFailure("Loss reports could not be read.", exception);
            }
        }

        @Override
        public void insert(LossReport report) {
            requireEntity(report, "Loss report");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO loss_reports(loan_id, description) VALUES (?, ?)")) {
                statement.setString(1, report.loanId().value());
                statement.setString(2, report.description());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw mapSqlFailure("Loss report could not be inserted.", exception);
            }
        }
    }

    private static Member mapMember(ResultSet resultSet) throws SQLException {
        String memberId = canonical(resultSet.getString("member_id"), "Member ID");
        String name = canonical(resultSet.getString("name"), "Member name");
        String encodedHash = resultSet.getString("password_hash");
        Instant removedAt = parseOptionalInstant(resultSet.getString("removed_at"), "Removed at");
        return Member.restore(new MemberId(memberId), name, new PasswordHash(encodedHash),
                readInteger(resultSet, "is_active", 0, 1) == 1, removedAt);
    }

    private static EquipmentType mapEquipmentType(ResultSet resultSet) throws SQLException {
        String nameValue = canonical(resultSet.getString("name"), "Equipment type name");
        EquipmentTypeName name = new EquipmentTypeName(nameValue);
        if (!name.comparisonKey().equals(resultSet.getString("comparison_key"))) {
            throw integrityFailure("Equipment type comparison key is inconsistent.");
        }
        return EquipmentType.restore(new EquipmentTypeId(
                canonical(resultSet.getString("equipment_type_id"), "Equipment type ID")), name,
                readInteger(resultSet, "is_offered", 0, 1) == 1);
    }

    private static EquipmentItem mapEquipmentItem(ResultSet resultSet) throws SQLException {
        return EquipmentItem.restore(
                new EquipmentId(canonical(resultSet.getString("equipment_id"), "Equipment ID")),
                new EquipmentTypeId(canonical(resultSet.getString("equipment_type_id"),
                        "Equipment type ID")),
                enumValue(EquipmentCondition.class, resultSet.getString("condition"),
                        "Equipment condition"),
                enumValue(EquipmentAvailability.class, resultSet.getString("availability"),
                        "Equipment availability"),
                readInteger(resultSet, "verification_pending", 0, 1) == 1,
                readInteger(resultSet, "is_retired", 0, 1) == 1,
                parseOptionalInstant(resultSet.getString("retired_at"), "Retired at"));
    }

    private static LoanRequest mapLoanRequest(ResultSet resultSet) throws SQLException {
        String details = resultSet.getString("details");
        if (details != null && (!details.equals(details.strip()) || details.isBlank())) {
            throw integrityFailure("Loan request details are not canonical.");
        }
        return LoanRequest.restore(
                new LoanRequestId(canonical(resultSet.getString("loan_request_id"),
                        "Loan request ID")),
                new MemberId(canonical(resultSet.getString("member_id"), "Member ID")),
                new EquipmentTypeId(canonical(resultSet.getString("equipment_type_id"),
                        "Equipment type ID")),
                readInteger(resultSet, "requested_quantity", 1, Integer.MAX_VALUE),
                parseDate(resultSet.getString("requested_start_date"), "Requested start date"),
                parseDate(resultSet.getString("requested_end_date"), "Requested end date"),
                details,
                parseInstant(resultSet.getString("requested_at"), "Requested at"),
                enumValue(LoanRequestStatus.class, resultSet.getString("status"),
                        "Loan request status"),
                readOptionalQuantity(resultSet, "approved_quantity"));
    }

    private static Loan mapLoan(ResultSet resultSet) throws SQLException {
        String condition = resultSet.getString("reported_return_condition");
        return Loan.restore(
                new LoanId(canonical(resultSet.getString("loan_id"), "Loan ID")),
                new LoanRequestId(canonical(resultSet.getString("loan_request_id"),
                        "Loan request ID")),
                new MemberId(canonical(resultSet.getString("member_id"), "Member ID")),
                new EquipmentId(canonical(resultSet.getString("equipment_id"), "Equipment ID")),
                parseInstant(resultSet.getString("started_at"), "Started at"),
                parseDate(resultSet.getString("end_date"), "End date"),
                enumValue(LoanStatus.class, resultSet.getString("status"), "Loan status"),
                condition == null ? null : enumValue(ReportedReturnCondition.class, condition,
                        "Reported return condition"));
    }

    private static DamageReport mapDamageReport(ResultSet resultSet) throws SQLException {
        String storageKey = canonical(resultSet.getString("storage_key"), "Damage storage key");
        String description = canonical(resultSet.getString("description"), "Damage description");
        return DamageReport.create(
                new LoanId(canonical(resultSet.getString("loan_id"), "Loan ID")),
                new DamageImageReference(storageKey,
                        enumValue(DamageImageFormat.class, resultSet.getString("image_format"),
                                "Damage image format"),
                        readInteger(resultSet, "size_bytes", 1, 5 * 1024 * 1024)),
                description);
    }

    private static LossReport mapLossReport(ResultSet resultSet) throws SQLException {
        return LossReport.create(
                new LoanId(canonical(resultSet.getString("loan_id"), "Loan ID")),
                canonical(resultSet.getString("description"), "Loss description"));
    }

    private static String canonical(String value, String fieldName) {
        if (value == null || !value.equals(value.strip()) || value.isBlank()) {
            throw integrityFailure(fieldName + " is not canonical.");
        }
        return value;
    }

    private static Instant parseInstant(String value, String fieldName) {
        try {
            return Instant.parse(canonical(value, fieldName));
        } catch (RuntimeException exception) {
            throw integrityFailure(fieldName + " is invalid.", exception);
        }
    }

    private static Instant parseOptionalInstant(String value, String fieldName) {
        return value == null ? null : parseInstant(value, fieldName);
    }

    private static LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(canonical(value, fieldName));
        } catch (RuntimeException exception) {
            throw integrityFailure(fieldName + " is invalid.", exception);
        }
    }

    /**
     * Reads an integer without silently coercing SQLite real, text, or oversized values.
     *
     * @param resultSet Current row.
     * @param column Column name.
     * @param minimum Minimum accepted value.
     * @param maximum Maximum accepted value.
     * @return Validated integer.
     * @throws SQLException If the column cannot be read.
     */
    private static int readInteger(ResultSet resultSet, String column, int minimum, int maximum)
            throws SQLException {
        Object value = resultSet.getObject(column);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw integrityFailure(column + " must contain an integer.");
        }
        long integer = ((Number) value).longValue();
        if (integer < minimum || integer > maximum) {
            throw integrityFailure(column + " is outside its valid range.");
        }
        return (int) integer;
    }

    /**
     * Reads a nullable positive quantity without numeric coercion.
     *
     * @param resultSet Current row.
     * @param column Quantity column.
     * @return Validated quantity, or null when absent.
     * @throws SQLException If the column cannot be read.
     */
    private static Integer readOptionalQuantity(ResultSet resultSet, String column)
            throws SQLException {
        return resultSet.getObject(column) == null ? null
                : readInteger(resultSet, column, 1, Integer.MAX_VALUE);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumType, String value,
            String fieldName) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (RuntimeException exception) {
            throw integrityFailure(fieldName + " is invalid.", exception);
        }
    }

    private static void requireEntity(Object entity, String entityName) {
        if (entity == null) {
            throw new IllegalArgumentException(entityName + " cannot be null.");
        }
    }

    private static void requireId(Object id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null.");
        }
    }

    private static ApplicationException integrityFailure(String message) {
        return integrityFailure(message, null);
    }

    private static ApplicationException integrityFailure(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message, cause);
    }
}
