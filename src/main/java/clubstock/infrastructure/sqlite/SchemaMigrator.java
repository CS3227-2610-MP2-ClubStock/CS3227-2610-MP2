package clubstock.infrastructure.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;

/**
 * Applies ordered classpath SQL migrations to a SQLite connection.
 */
final class SchemaMigrator {
    private static final int LATEST_VERSION = 1;
    private static final String MIGRATION_PREFIX =
            "/clubstock/infrastructure/sqlite/migration/";

    /**
     * Applies all migrations required by the current schema.
     *
     * @param connection Open SQLite connection.
     * @return Whether this connection opened an empty, unversioned database.
     */
    boolean migrate(Connection connection) {
        int currentVersion = readUserVersion(connection);
        if (currentVersion > LATEST_VERSION) {
            throw persistenceFailure("Database schema version is newer than this application.",
                    null);
        }
        boolean isFreshDatabase = currentVersion == 0;
        if (isFreshDatabase && hasExistingSchema(connection)) {
            throw persistenceFailure("Unversioned database already contains a schema.", null);
        }

        for (int version = currentVersion + 1; version <= LATEST_VERSION; version++) {
            applyMigration(connection, version);
            setUserVersion(connection, version);
        }
        return isFreshDatabase;
    }

    /**
     * Checks whether an unversioned database already contains application objects.
     *
     * @param connection Open SQLite connection.
     * @return Whether a preexisting schema would make initial seeding unsafe.
     */
    private boolean hasExistingSchema(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT 1 FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%' LIMIT 1")) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw persistenceFailure("Existing database schema could not be checked.", exception);
        }
    }

    /**
     * Returns the SQLite user-version value.
     *
     * @param connection Open SQLite connection.
     * @return Current schema version.
     */
    private int readUserVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (!resultSet.next()) {
                throw persistenceFailure("Database schema version could not be read.", null);
            }

            return resultSet.getInt(1);
        } catch (SQLException exception) {
            throw persistenceFailure("Database schema version could not be read.", exception);
        }
    }

    /**
     * Applies one versioned SQL resource.
     *
     * @param connection Open SQLite connection.
     * @param version Migration version.
     */
    private void applyMigration(Connection connection, int version) {
        String resourceName = MIGRATION_PREFIX + String.format("V%03d__initial_schema.sql", version);
        try (InputStream stream = SchemaMigrator.class.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw persistenceFailure("Required database migration is missing.", null);
            }

            String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            executeScript(connection, script);
        } catch (IOException | SQLException exception) {
            throw persistenceFailure("Database migration failed.", exception);
        }
    }

    /**
     * Executes statements from a migration while keeping trigger bodies intact.
     *
     * @param connection Open SQLite connection.
     * @param script Migration script.
     * @throws SQLException If a statement fails.
     */
    private void executeScript(Connection connection, String script) throws SQLException {
        String[] statements = script.split("(?<=;)\\s*(?=(?:CREATE|INSERT|PRAGMA)\\b)");
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /**
     * Writes the current migration version.
     *
     * @param connection Open SQLite connection.
     * @param version Migration version.
     */
    private void setUserVersion(Connection connection, int version) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        } catch (SQLException exception) {
            throw persistenceFailure("Database schema version could not be updated.", exception);
        }
    }

    /**
     * Creates a safe persistence failure.
     *
     * @param message Safe display message.
     * @param cause Technical cause.
     * @return Persistence failure.
     */
    private static ApplicationException persistenceFailure(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message, cause);
    }
}
