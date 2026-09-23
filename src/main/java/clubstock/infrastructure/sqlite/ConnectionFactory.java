package clubstock.infrastructure.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens a configured SQLite connection for one transaction boundary.
 */
@FunctionalInterface
interface ConnectionFactory {

    /**
     * Opens a connection.
     *
     * @param isWrite Whether the connection will execute writes.
     * @return Open connection.
     * @throws SQLException If the connection cannot be opened.
     */
    Connection open(boolean isWrite) throws SQLException;
}
