package clubstock.infrastructure.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.TransactionMode;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.application.port.UnitOfWorkOperation;

/**
 * Opens ClubStock's SQLite database and owns repository transaction boundaries.
 */
public final class SqliteDatabase implements TransactionManager {
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private final Path databasePath;
    private final ConnectionFactory connectionFactory;

    /**
     * Creates a database adapter for the supplied file.
     *
     * @param databasePath SQLite database file.
     */
    public SqliteDatabase(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path cannot be null.");
        }

        this.databasePath = databasePath.toAbsolutePath().normalize();
        connectionFactory = this::openConnection;
    }

    /**
     * Creates a database adapter with an injectable connection factory for deterministic tests.
     *
     * @param databasePath SQLite database file.
     * @param connectionFactory Connection factory.
     */
    SqliteDatabase(Path databasePath, ConnectionFactory connectionFactory) {
        if (databasePath == null || connectionFactory == null) {
            throw new IllegalArgumentException("Database configuration cannot be null.");
        }

        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.connectionFactory = connectionFactory;
    }

    /**
     * Creates or migrates the database and validates its stored state.
     */
    public void initialize() {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw persistenceFailure("ClubStock storage could not be prepared.", exception);
        }

        try (Connection connection = connectionFactory.open(true)) {
            connection.setAutoCommit(false);
            try {
                boolean isFreshDatabase = new SchemaMigrator().migrate(connection);
                if (isFreshDatabase) {
                    seedExcoAccount(connection);
                }
                new SqliteUnitOfWork(connection).validateStoredRows();
                new SqliteIntegrityChecker().check(connection);
                if (!isFreshDatabase) {
                    verifyWriteAccess(connection);
                }
                connection.commit();
            } catch (ApplicationException | SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                if (exception instanceof ApplicationException applicationException) {
                    throw applicationException;
                }
                throw persistenceFailure("ClubStock storage could not be initialized.", exception);
            }
        } catch (ApplicationException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw persistenceFailure("ClubStock storage could not be initialized.", exception);
        }
    }

    /**
     * Executes a consistent read transaction.
     *
     * @param operation Read callback.
     * @param <T> Result type.
     * @return Callback result.
     */
    @Override
    public <T> T read(UnitOfWorkOperation<T> operation) {
        return execute(operation, false);
    }

    /**
     * Executes an atomic write transaction.
     *
     * @param operation Write callback.
     * @param <T> Result type.
     * @return Callback result after commit.
     */
    @Override
    public <T> T write(UnitOfWorkOperation<T> operation) {
        return execute(operation, true);
    }

    /**
     * Opens a configured Xerial connection.
     *
     * @param isWrite Whether the connection will write.
     * @return Open connection.
     * @throws SQLException If the connection cannot be opened.
     */
    private Connection openConnection(boolean isWrite) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        config.setTransactionMode(isWrite ? TransactionMode.IMMEDIATE : TransactionMode.DEFERRED);
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath, config.toProperties());
    }

    /**
     * Executes one transaction and maps JDBC failures to safe application errors.
     *
     * @param operation Transaction callback.
     * @param isWrite Whether the transaction writes.
     * @param <T> Result type.
     * @return Callback result.
     */
    private <T> T execute(UnitOfWorkOperation<T> operation, boolean isWrite) {
        if (operation == null) {
            throw new IllegalArgumentException("Transaction operation cannot be null.");
        }

        boolean hasCommitted = false;
        try (Connection connection = connectionFactory.open(isWrite)) {
            if (!isWrite) {
                enableQueryOnly(connection);
            }
            connection.setAutoCommit(false);
            UnitOfWork unitOfWork = new SqliteUnitOfWork(connection);
            try {
                T result = operation.execute(unitOfWork);
                if (isWrite) {
                    new SqliteIntegrityChecker().checkBeforeCommit(connection);
                    connection.commit();
                    hasCommitted = true;
                } else {
                    connection.rollback();
                }
                return result;
            } catch (ApplicationException exception) {
                throw rollbackAndAttach(connection, exception);
            } catch (RuntimeException exception) {
                throw rollbackAndMapUnexpected(connection, exception);
            } catch (SQLException exception) {
                ApplicationException failure = persistenceFailure(
                        "ClubStock storage operation failed.", exception);
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    failure.addSuppressed(rollbackException);
                }
                throw failure.withTransactionOutcome(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN);
            }
        } catch (ApplicationException exception) {
            throw exception;
        } catch (SQLException exception) {
            ApplicationException failure = persistenceFailure(
                    "ClubStock storage operation failed.", exception);
            if (isWrite) {
                TransactionOutcome outcome = hasCommitted ? TransactionOutcome.COMMITTED
                        : TransactionOutcome.CONFIRMED_ROLLBACK;
                throw failure.withTransactionOutcome(outcome);
            }
            throw failure;
        }
    }

    /**
     * Prevents a read callback from executing SQL writes on its connection.
     *
     * @param connection Open SQLite connection.
     * @throws SQLException If SQLite cannot enable query-only mode.
     */
    private void enableQueryOnly(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only = ON");
        }
    }

    /**
     * Rolls back an application failure and attaches the known outcome.
     *
     * @param connection Active connection.
     * @param exception Application failure.
     * @return Failure carrying transaction outcome.
     */
    private ApplicationException rollbackAndAttach(Connection connection,
            ApplicationException exception) {
        return exception.withTransactionOutcome(rollback(connection));
    }

    /**
     * Rolls back an unexpected failure and exposes its transaction outcome.
     *
     * @param connection Active connection.
     * @param exception Unexpected failure.
     * @return Safe failure carrying the original cause and transaction outcome.
     */
    private ApplicationException rollbackAndMapUnexpected(Connection connection,
            RuntimeException exception) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                "ClubStock storage operation failed.", exception, rollback(connection));
    }

    /**
     * Attempts rollback and conservatively classifies the outcome.
     *
     * @param connection Active connection.
     * @return Known transaction outcome.
     */
    private TransactionOutcome rollback(Connection connection) {
        try {
            connection.rollback();
            return TransactionOutcome.CONFIRMED_ROLLBACK;
        } catch (SQLException exception) {
            return TransactionOutcome.COMMIT_OUTCOME_UNKNOWN;
        }
    }

    /**
     * Inserts the singleton Exco row while creating a fresh database.
     *
     * @param connection Active connection.
     * @throws SQLException If the row cannot be inserted.
     */
    private void seedExcoAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO exco_account(singleton_id, password_hash) VALUES (1, NULL)")) {
            statement.executeUpdate();
        }
    }

    /**
     * Confirms an existing database accepts writes without changing stored account data.
     *
     * @param connection Active connection.
     * @throws SQLException If the database is read-only or otherwise unwritable.
     */
    private void verifyWriteAccess(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE exco_account SET password_hash = password_hash WHERE singleton_id = 1")) {
            if (statement.executeUpdate() != 1) {
                throw persistenceFailure("Singleton Exco account is missing.", null);
            }
        }
    }

    /**
     * Creates a persistence failure.
     *
     * @param message Safe display message.
     * @param cause Technical cause.
     * @return Persistence failure.
     */
    private static ApplicationException persistenceFailure(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message, cause);
    }
}
