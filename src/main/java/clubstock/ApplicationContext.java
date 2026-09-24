package clubstock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.infrastructure.sqlite.SqliteDatabase;
import clubstock.ui.auth.AuthenticationGateway;
import clubstock.ui.auth.AuthenticationGatewayAdapter;

/**
 * Holds dependencies constructed once for one ClubStock process.
 */
public final class ApplicationContext {
    private static final String DATA_DIRECTORY_PROPERTY = "clubstock.dataDir";
    private static final String DATABASE_FILENAME = "clubstock.db";
    private final Path dataDirectory;
    private final Clock clock;
    private final ZoneId zoneId;
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final AuthenticationGateway authentication;

    private ApplicationContext(Path dataDirectory, Clock clock, ZoneId zoneId,
            TransactionManager transactionManager, SessionManager sessionManager,
            AuthenticationGateway authentication) {
        this.dataDirectory = dataDirectory;
        this.clock = clock;
        this.zoneId = zoneId;
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.authentication = authentication;
    }

    /**
     * Creates the production context and initializes persistent storage.
     *
     * @return Fully initialized application context.
     */
    public static ApplicationContext createProduction() {
        Path dataDirectory = resolveDataDirectory();
        return create(dataDirectory);
    }

    /**
     * Creates an isolated application context with real persistence and authentication services.
     *
     * @param dataDirectory Disposable or otherwise controlled data directory.
     * @return Fully initialized application context.
     */
    public static ApplicationContext create(Path dataDirectory) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("Application data directory cannot be null.");
        }

        Path normalizedDirectory = dataDirectory.toAbsolutePath().normalize();
        SqliteDatabase database = initializeDatabase(normalizedDirectory);
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authenticationService = new AuthenticationService(database,
                new Pbkdf2PasswordHasher(), sessionManager);
        AuthenticationGateway authentication = new AuthenticationGatewayAdapter(
                authenticationService);
        return new ApplicationContext(normalizedDirectory, Clock.systemDefaultZone(),
                ZoneId.systemDefault(), database, sessionManager, authentication);
    }

    /**
     * Returns the managed data directory.
     *
     * @return Absolute normalized directory.
     */
    public Path dataDirectory() {
        return dataDirectory;
    }

    /**
     * Returns the process clock captured during composition.
     *
     * @return Application clock.
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns the process time zone captured during composition.
     *
     * @return Application time zone.
     */
    public ZoneId zoneId() {
        return zoneId;
    }

    /**
     * Returns the authentication integration boundary.
     *
     * @return Authentication boundary.
     */
    public AuthenticationGateway authentication() {
        return authentication;
    }

    /**
     * Returns the transaction manager shared by application services.
     *
     * @return Shared transaction manager.
     */
    public TransactionManager transactionManager() {
        return transactionManager;
    }

    /**
     * Returns the session manager shared by application services and authentication.
     *
     * @return Shared in-memory session manager.
     */
    public SessionManager sessionManager() {
        return sessionManager;
    }

    private static Path resolveDataDirectory() {
        String configuredDirectory = System.getProperty(DATA_DIRECTORY_PROPERTY);
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory).toAbsolutePath().normalize();
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "ClubStock storage could not be located.", null);
        }
        return Path.of(userHome, ".clubstock").toAbsolutePath().normalize();
    }

    /**
     * Initializes the SQLite database in the configured data directory.
     *
     * @param dataDirectory Application data directory.
     * @return Initialized database and transaction manager.
     */
    private static SqliteDatabase initializeDatabase(Path dataDirectory) {
        SqliteDatabase database = new SqliteDatabase(dataDirectory.resolve(DATABASE_FILENAME));
        database.initialize();
        return database;
    }
}
