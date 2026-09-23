package clubstock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.port.TransactionManager;
import clubstock.infrastructure.sqlite.SqliteDatabase;
import clubstock.ui.auth.AuthenticationGateway;
import clubstock.ui.auth.AuthenticationGatewayProvider;
import clubstock.ui.auth.UnavailableAuthenticationGateway;

/**
 * Holds dependencies constructed once for one ClubStock process.
 */
public final class ApplicationContext {
    private static final String DATA_DIRECTORY_PROPERTY = "clubstock.dataDir";
    private static final String DATABASE_FILENAME = "clubstock.db";
    private final Path dataDirectory;
    private final Clock clock;
    private final ZoneId zoneId;
    private final AuthenticationGateway authentication;

    private ApplicationContext(Path dataDirectory, Clock clock, ZoneId zoneId,
            AuthenticationGateway authentication) {
        this.dataDirectory = dataDirectory;
        this.clock = clock;
        this.zoneId = zoneId;
        this.authentication = authentication;
    }

    /**
     * Creates the production context and initializes persistent storage.
     *
     * @return Fully initialized application context.
     */
    public static ApplicationContext createProduction() {
        Path dataDirectory = resolveDataDirectory();
        SqliteDatabase database = initializeDatabase(dataDirectory);
        AuthenticationGateway authentication = loadAuthenticationGateway(database);
        return new ApplicationContext(dataDirectory, Clock.systemDefaultZone(),
                ZoneId.systemDefault(), authentication);
    }

    /**
     * Creates an isolated context with an injected authentication boundary.
     *
     * @param dataDirectory Disposable or otherwise controlled data directory.
     * @param authentication Authentication boundary.
     * @return Fully initialized application context.
     */
    public static ApplicationContext create(Path dataDirectory,
            AuthenticationGateway authentication) {
        if (dataDirectory == null || authentication == null) {
            throw new IllegalArgumentException("Application context dependencies cannot be null.");
        }

        Path normalizedDirectory = dataDirectory.toAbsolutePath().normalize();
        initializeDatabase(normalizedDirectory);
        return new ApplicationContext(normalizedDirectory, Clock.systemDefaultZone(),
                ZoneId.systemDefault(), authentication);
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

    private static SqliteDatabase initializeDatabase(Path dataDirectory) {
        SqliteDatabase database = new SqliteDatabase(dataDirectory.resolve(DATABASE_FILENAME));
        database.initialize();
        return database;
    }

    private static AuthenticationGateway loadAuthenticationGateway(
            TransactionManager transactionManager) {
        List<AuthenticationGatewayProvider> providers = new ArrayList<>();
        ServiceLoader.load(AuthenticationGatewayProvider.class).forEach(providers::add);
        if (providers.isEmpty()) {
            return new UnavailableAuthenticationGateway();
        }
        if (providers.size() > 1) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "ClubStock authentication could not be initialized.", null);
        }

        AuthenticationGateway gateway = providers.getFirst().create(transactionManager);
        if (gateway == null) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "ClubStock authentication could not be initialized.", null);
        }
        return gateway;
    }
}
